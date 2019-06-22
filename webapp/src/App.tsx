import axios from 'axios'
import dateFormat from 'dateformat'
import * as  L from 'leaflet';
import * as React from 'react';
// @ts-ignore
import {Map, Marker, Popup, TileLayer} from "react-leaflet";
import shortid from 'shortid'
import './App.css';
import AtmPopup from "./AtmPopup";
import {Event, Props} from './Event'
import notesBig from './notes-x2.png'
import notes from './notes.png'

interface HourlyAtm {
    load: number
}

export interface Atm {
    currentAmount: number
    location: number[]
    name: string
    refillAmount: number
    refillDelayHours: number
    atmDefaultLoad: number
    hourly: { string: HourlyAtm }
}

interface State {
    readonly config: any
    atms: Atm[]
    events: Props[]
    eventsPerHour: number
    zoom: number
    interval: number
    selectedDate: Date
    selectedHour: number
    startDate: Date
    startHour: number
    endDate: Date
    endHour: number
    playSpeed: number
    paused: boolean
    editing: boolean
    simulationName: string
    simulationTime: Date
}

const cracowLocation = [50.06143, 19.944544];

const icon = L.icon({
    iconRetinaUrl: notesBig,
    iconSize: [48, 48],
    iconUrl: notes
});

const sideEffectTypes = ["out-of-money", "not-enough-money", "refill"];

const server = "localhost:8080";

class App extends React.Component<any, State> {

    private static isSideEffect(m) {
        return sideEffectTypes.includes(m.eventType);
    }

    private static datepickerFormat(selectedDate) {
        return dateFormat(selectedDate, "yyyy-mm-dd");
    }

    public now = new Date();

    public state: State = {
        atms: [],
        config: {},
        editing: true,
        endDate: new Date(this.now.getFullYear(), this.now.getMonth(), this.now.getDate() + 7),
        endHour: this.now.getHours(),
        events: [],
        eventsPerHour: 100,
        interval: 1000,
        paused: false,
        playSpeed: 1,
        selectedDate: this.now,
        selectedHour: this.now.getHours(),
        simulationName: "simulation",
        simulationTime: new Date(),
        startDate: this.now,
        startHour: this.now.getHours(),
        zoom: 14
    };

    private websocket;

    public componentDidMount(): void {
        const path = window.location.pathname;
        console.log(`Path is: ${path}`);

        const editing = path === "/";
        this.setState(s => {
            return {...s, editing}
        });

        if (editing) {
            this.loadConfig();
        } else {
            this.loadConfig(path);
            this.websocket = new WebSocket(`ws://${server}/websocket${path}`);
            this.websocket.onopen = () => this.websocket.send("hello");
            this.websocket.onmessage = (m) => {
                if (m.data === "ping") {
                    console.log("ping")
                } else {
                    const events: Props[] = JSON.parse(m.data);
                    console.log(`Received ${events.length} events`);

                    this.processEvents(events);
                }
            }
        }
    }

    public render() {
        return (
            <div className="App">
                <div className="map-with-events">
                    <Map center={cracowLocation} zoom={this.state.zoom}>
                        <TileLayer
                            attribution='&amp;copy <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />
                        {this.atms()}

                    </Map>
                    <div className="right-panel">
                        {this.state.editing ? this.editPanel() : this.playbackPanel()}
                    </div>
                </div>
            </div>
        );
    }

    private processEvents(events: Props[]) {
        if (events.length === 0) {
            return;
        }

        if (this.state.paused) {
            window.setTimeout(()=> this.processEvents(events), 100);
        } else {

            this.setState(s => {
                return {...s, simulationTime: new Date(events[0].time)}
            });

            const endOfHourEventId = events.findIndex(e => e.time !== this.state.simulationTime.getTime());

            if (endOfHourEventId !== -1) {
                console.log(`Found end of hour on id ${endOfHourEventId}`);

                const thisHourEvents = events.slice(0, endOfHourEventId);
                const followingHourEvents = events.slice(endOfHourEventId);

                console.log(`${thisHourEvents.length} in current hour, ${followingHourEvents.length} remaining`);
                this.updateGuiState(thisHourEvents);

                window.setTimeout(() => {
                    this.processEvents(followingHourEvents);
                }, this.state.interval);
            } else {
                console.log("Full batch in current hour");
                this.updateGuiState(events);
                console.log("Sending batch finished from full batch path");
                this.websocket.send("Batch finished")
            }
        }
    }

    private updateGuiState(events) {
        const atmEvents = {};
        const sideEffects: Props[] = [];
        events.forEach((event: Props) => {
            if (App.isSideEffect(event)) {
                event.id = `${event.atm}-${event.time}-${event.eventType}-${shortid.generate()}`;
                sideEffects.push(event)
            }

            const balance = "balance";
            if (event[balance] !== undefined) {
                if (atmEvents[event.atm] === undefined) {
                    atmEvents[event.atm] = [event]
                } else {
                    atmEvents[event.atm].push(event)
                }
            }
        });

        const atms = this.state.atms.map(atm => {
            const currentAtmEvents = atmEvents[atm.name];
            if (currentAtmEvents !== undefined) {
                const currentAmount = currentAtmEvents[currentAtmEvents.length - 1].balance;
                return {...atm, currentAmount}
            }

            return atm;
        });

        this.setState((s) => {
            const newEvents = sideEffects.reverse().concat(s.events).slice(0, 100);
            return {...s, atms, events: newEvents}
        });
    }

    private playbackPanel() {
        return <div>
            <div>
                <div>
                    Simulation time: {this.state.simulationTime.toLocaleString()}
                </div>
                <div>
                    Simulation speed
                    <button onClick={this.decelerate}>-</button>
                    {this.state.paused ?
                        <button onClick={this.resume}>▶</button>
                        : <button onClick={this.pause}>||</button>}
                    <button onClick={this.accelerate}>+</button>
                </div>
            </div>
            <div className="Events-banner">
                Events
            </div>
            <div className="Events-container">
                {this.state.events
                    .map((m: Props) => <Event key={m.id} eventData={m}/>)
                }
            </div>
        </div>;
    }

    private editPanel() {
        return <div>
            <div>Start date: <input type="date" name="startDate"
                                    value={App.datepickerFormat(this.state.startDate)}
                                    onChange={this.startDateChanged}/>
            </div>
            <div>Start hour: <input type="number" name="startHour"
                                    min="0"
                                    max="24"
                                    value={this.state.startHour}
                                    onChange={this.startHourChanged}/>
            </div>
            <div>End date: <input type="date" name="endDate"
                                  value={App.datepickerFormat(this.state.endDate)}
                                  onChange={this.endDateChanged}/>
            </div>
            <div>End hour: <input type="number" name="endHour"
                                  min="0"
                                  max="24"
                                  value={this.state.endHour}
                                  onChange={this.endHourChanged}/>
            </div>
            <div>Withdrawals per hour: <input type="number" name="eventsPerHour"
                                              min="1"
                                              max="1000"
                                              value={this.state.eventsPerHour}
                                              onChange={this.eventsPerHourChanged}/>
            </div>
            <div>Simulation name: <input type="string" name="simulationName"
                                         value={this.state.simulationName}
                                         onChange={this.simulationNameChanged}/>
            </div>
            <div>
                <button onClick={this.startSimulation}>Start simulation</button>
            </div>
        </div>;
    }

    private loadConfig(simulationPath: string = "/default") {
        console.log(`Loading config: ${simulationPath}`);
        axios.get(`http://${server}/config${simulationPath}`,
            {headers: {Accept: "application/json"}})
            .then(r => {
                this.setState(s => {
                    const atms = r.data.atms.map(a => {
                        return {...a, currentAmount: a.refillAmount}
                    });
                    const startDate = new Date(r.data.startDate);
                    const startHour = startDate.getHours();
                    const endDate = new Date(r.data.endDate);
                    const endHour = endDate.getHours();
                    return {
                        ...s, atms, config: r.data, endDate, endHour, simulationTime: startDate,
                        startDate, startHour
                    }
                });
            });
    }

    private refillAmountChanged(atm) {
        return this.withChangedValueFromEvent(atm, "refillAmount");
    }

    private refillDelayHoursChanged(atm) {
        return this.withChangedValueFromEvent(atm, "refillDelayHours");
    }

    private atmDefaultLoadChanged(atm) {
        return this.withChangedValueFromEvent(atm, "atmDefaultLoad");
    }

    private scheduledRefillIntervalChanged(atm) {
        return this.withChangedValueFromEvent(atm, "scheduledRefillInterval");
    }

    private hourlyLoadChanged(atm, timestamp: number) {
        return e => {
            console.log(`Hourly load at ${timestamp} changed to ${e.target.value}`);
            const atms = this.state.atms.map(x => {
                if (x.name === atm.name) {
                    const changedAtm = {...atm};
                    const changedHourly = {...changedAtm.hourly[timestamp]};
                    changedHourly.load = Number.parseInt(e.target.value, undefined);
                    changedAtm.hourly[timestamp] = changedHourly;
                    return changedAtm;
                } else {
                    return x
                }
            });
            this.setState({...this.state, atms})
        }
    }

    private selectedDateChanged = (e) => {
        console.log(`Selected date changed to ${e.target.value}`);
        const selectedDate = new Date(e.target.value);
        this.setState({...this.state, selectedDate})
    };

    private selectedHourChanged = (e) => {
        console.log(`Selected hour changed to ${e.target.value}`);
        const selectedHour = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, selectedHour});
    };

    private startDateChanged = (e) => {
        console.log(`Start date changed to ${e.target.value}`);
        const startDate = new Date(e.target.value);
        this.setState({...this.state, startDate})
    };

    private startHourChanged = (e) => {
        console.log(`Start hour changed to ${e.target.value}`);
        const startHour = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, startHour});
    };

    private endDateChanged = (e) => {
        console.log(`End date changed to ${e.target.value}`);
        const endDate = new Date(e.target.value);
        this.setState({...this.state, endDate})
    };

    private endHourChanged = (e) => {
        console.log(`End hour changed to ${e.target.value}`);
        const endHour = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, endHour});
    };

    private eventsPerHourChanged = (e) => {
        console.log(`Events per hour changed to ${e.target.value}`);
        const eventsPerHour = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, eventsPerHour});
    };

    private simulationNameChanged = (e) => {
        console.log(`Simulation name changed to ${e.target.value}`);
        this.setState({...this.state, simulationName: e.target.value});
    };

    private pause = () => {
        console.log("Pause pressed");
        const paused = true;
        this.setState({...this.state, paused});
    };

    private resume = () => {
        console.log("Resume pressed");
        const paused = false;
        this.setState({...this.state, paused});
    };

    private withChangedValueFromEvent(atm, field: string) {
        return e => {
            console.log(`Field ${field} changed to ${e.target.value}`);
            const atms = this.state.atms.map(x => {
                if (x.name === atm.name) {
                    const changedAtm = {...atm};
                    changedAtm[field] = Number.parseInt(e.target.value, undefined);
                    return changedAtm;
                } else {
                    return x
                }
            });
            this.setState(s => {
                return {...s, atms}
            })
        }
    }

    private atms() {
        const selectedDate = this.state.selectedDate;

        return this.state.atms.map((a) =>
            <Marker key={a.name} position={a.location} icon={icon}>
                <Popup>
                    <AtmPopup atm={a}
                              default={this.state.config.default}
                              editing={this.state.editing}
                              selectedDate={App.datepickerFormat(selectedDate)}
                              selectedHour={this.state.selectedHour}
                              refillAmountChanged={this.refillAmountChanged(a)}
                              refillDelayHoursChanged={this.refillDelayHoursChanged(a)}
                              atmDefaultLoadChanged={this.atmDefaultLoadChanged(a)}
                              selectedDateChanged={this.selectedDateChanged}
                              selectedHourChanged={this.selectedHourChanged}
                              hourlyLoadChanged={this.hourlyLoadChanged(a, this.getTimestamp())}
                              scheduledRefillIntervalChanged={this.scheduledRefillIntervalChanged(a)}
                    />
                </Popup>
            </Marker>
        );
    }

    private getTimestamp(): number {
        const date = new Date(this.state.selectedDate.getTime());
        date.setHours(this.state.selectedHour, 0, 0, 0);
        return date.getTime();
    }

    private startSimulation = () => {
        const startDate = new Date(this.state.startDate);
        startDate.setHours(this.state.startHour, 0, 0, 0);
        const endDate = new Date(this.state.endDate);
        endDate.setHours(this.state.endHour, 0, 0, 0);
        axios.post(`http://${server}/simulation/${this.state.simulationName}`,
            {
                atms: this.state.atms,
                default: this.state.config.default,
                endDate,
                eventsPerHour: this.state.eventsPerHour,
                startDate,
                withdrawal: this.state.config.withdrawal,
            })
            .then(response => console.log(`Simulation response ${JSON.stringify(response)}`))
            .catch(errorResponse => `Simulation error ${errorResponse}`)
    };

    private accelerate = () => {
        this.setState(s => {
            const playSpeed = s.playSpeed * 2;
            const interval = Math.floor(1000 / playSpeed);
            return {...s, playSpeed, interval}
        })
    };

    private decelerate = () => {
        this.setState(s => {
            const playSpeed = s.playSpeed / 2;
            const interval = Math.floor(1000 / playSpeed);
            return {...s, playSpeed, interval}
        })
    };
}

export default App;
