import axios from 'axios'
import dateFormat from 'dateformat'
import * as  L from 'leaflet';
import * as React from 'react';
// @ts-ignore
import {Map, Marker, Popup, TileLayer} from "react-leaflet";
import './App.css';
import AtmPopup from "./AtmPopup";
import {Event, Props} from './Event'
import notesBig from './notes-x2.png'
import notes from './notes.png'

interface HourlyAtm {
    load: number
}

export interface Atm {
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
    zoom: number
    interval: number,
    selectedDate: Date
    selectedHour: number
    startDate: Date
    startHour: number
    endDate: Date
    endHour: number
    playSpeed: number
    paused: boolean
}

const cracowLocation = [50.06143, 19.944544];

const icon = L.icon({
    iconRetinaUrl: notesBig,
    iconSize: [48, 48], // size of the icon,
    iconUrl: notes
});

const sideEffects = ["out-of-money", "not-enough-money", "refill"];

const server = "localhost:8080";

class App extends React.Component<any, State> {

    private static isSideEffect(m) {
        return sideEffects.includes(m.eventType);
    }

    private static datepickerFormat(selectedDate) {
        return dateFormat(selectedDate, "yyyy-mm-dd");
    }

    public now = new Date();

    public state: State = {
        atms: [],
        config: {},
        endDate: new Date(this.now.getFullYear(), this.now.getMonth(), this.now.getDate() + 7),
        endHour: this.now.getHours(),
        events: [],
        interval: 1000,
        paused: false,
        playSpeed: 1,
        selectedDate: this.now,
        selectedHour: this.now.getHours(),
        startDate: this.now,
        startHour: this.now.getHours(),
        zoom: 14
    };

    private websocket;

    public componentDidMount(): void {
        this.loadConfig();
        this.websocket = new WebSocket(`ws://${server}/websocket/output`);
        this.websocket.onopen = () => this.websocket.send("hello");
        this.websocket.onmessage = (m) => {
            if (m.data === "ping") {
                console.log("ping")
            } else {
                const events = JSON.parse(m.data);
                const sideEffectEvents = events.filter(e => App.isSideEffect(e));
                this.addToSideEffectsBox(sideEffectEvents, 0);

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
                        <div>
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
                            <div>
                                <button onClick={this.startSimulation}>Start simulation</button>
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
                                .map((m: Props, i) => <Event key={i} eventData={m}/>)
                            }
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    private addToSideEffectsBox(events, index: number) {
        window.setTimeout(() => {
            this.setState((s) => {
                return {...s, events: [events[index], ...s.events]}
            });
            if (index < events.length - 1) {
                window.setTimeout(() => this.addToSideEffectsBox(events, index + 1),
                    this.state.interval);
            } else {
                this.websocket.send("Batch finished")
            }
        }, this.state.interval);
    }

    private loadConfig() {
        axios.get(`http://${server}/config`,
            {headers: {Accept: "application/json"}})
            .then(r => {
                this.setState(s => {
                    return {...s, atms: r.data.atms, config: r.data}
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

    private pause = () => {
        console.log("Pause pressed");
        const paused = true;
        const interval = 9999999999999;
        this.setState({...this.state, paused, interval});
    };

    private resume = () => {
        console.log("Resume pressed");
        const paused = false;
        const interval = Math.floor(1000 / this.state.playSpeed);
        this.setState({...this.state, paused, interval});
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
                              selectedDate={App.datepickerFormat(selectedDate)}
                              selectedHour={this.state.selectedHour}
                              refillAmountChanged={this.refillAmountChanged(a)}
                              refillDelayHoursChanged={this.refillDelayHoursChanged(a)}
                              atmDefaultLoadChanged={this.atmDefaultLoadChanged(a)}
                              selectedDateChanged={this.selectedDateChanged}
                              selectedHourChanged={this.selectedHourChanged}
                              hourlyLoadChanged={this.hourlyLoadChanged(a, this.getTimestamp())}
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
        axios.post(`http://${server}/simulation/a`,
            {
                atms: this.state.atms,
                default: this.state.config.default,
                endDate: this.state.endDate,
                endHour: this.state.endHour,
                startDate: this.state.startDate,
                startHour: this.state.selectedHour,
                withdrawal: this.state.config.withdrawal,
            })
            .then(response => console.log(`Simulation response ${response}`))
            .catch(errorResponse => `Simulation error ${errorResponse}`)
    };

    private accelerate = () => {
        this.setState(s => {
            const playSpeed = s.playSpeed * 10;
            const interval = Math.floor(1000 / playSpeed);
            return {...s, playSpeed, interval}
        })
    };

    private decelerate = () => {
        this.setState(s => {
            const playSpeed = s.playSpeed / 10;
            const interval = Math.floor(1000 / playSpeed);
            return {...s, playSpeed, interval}
        })
    };
}

export default App;
