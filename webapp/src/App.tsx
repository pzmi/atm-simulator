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
import atmBlueAlert from './icons/atm-blue-alert.png'
import atmBlue from './icons/atm-blue.png'
import atmGreenAlert from './icons/atm-green-alert.png'
import atmGreen from './icons/atm-green.png'
import atmRedAlert from './icons/atm-red-alert.png'
import atmRed from './icons/atm-red.png'
import atmYellowAlert from './icons/atm-yellow-alert.png'
import atmYellow from './icons/atm-yellow.png'
import {LoadOptions} from "./LoadOptions";

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
    state: string
}

export interface Withdrawal {
    min: number
    max: number
    mean: number
    stddev: number
    distribution: string
}

interface Defaults {
    refillAmount: number
    refillDelayHours: number
    load: number
    scheduledRefillInterval: number
}

interface State {
    readonly config: any
    atms: Atm[]
    default: Defaults
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
    randomSeed: number
    editing: boolean
    simulationName: string
    simulationTime: Date
    withdrawal: Withdrawal
}

const cracowLocation = [50.06143, 19.944544];

const atmLow = L.icon({
    iconRetinaUrl: atmRed,
    iconSize: [48, 48],
    iconUrl: atmRed
});

const atmMid = L.icon({
    iconRetinaUrl: atmYellow,
    iconSize: [48, 48],
    iconUrl: atmYellow
});

const atmHigh = L.icon({
    iconRetinaUrl: atmBlue,
    iconSize: [48, 48],
    iconUrl: atmBlue
});

const atmFull = L.icon({
    iconRetinaUrl: atmGreen,
    iconSize: [48, 48],
    iconUrl: atmGreen
});

const atmLowAlert = L.icon({
    iconRetinaUrl: atmRedAlert,
    iconSize: [48, 48],
    iconUrl: atmRedAlert
});

const atmMidAlert = L.icon({
    iconRetinaUrl: atmYellowAlert,
    iconSize: [48, 48],
    iconUrl: atmYellowAlert
});

const atmHighAlert = L.icon({
    iconRetinaUrl: atmBlueAlert,
    iconSize: [48, 48],
    iconUrl: atmBlueAlert
});

const atmFullAlert = L.icon({
    iconRetinaUrl: atmGreenAlert,
    iconSize: [48, 48],
    iconUrl: atmGreenAlert
});

const sideEffectTypes = ["out-of-money", "not-enough-money"];
const withdrawalDistributionTypes = ["Gaussian", "Uniform"];

const server = "localhost:8080";

class App extends React.Component<any, State> {

    private static isSideEffect(m) {
        return sideEffectTypes.includes(m.eventType);
    }

    private static datepickerFormat(selectedDate) {
        return dateFormat(selectedDate, "yyyy-mm-dd");
    }

    private static getIcon(atm: Atm, defaultRefillAmount) {
        const isAlert = App.isAlert(atm);
        const refillAmount = atm.refillAmount ? atm.refillAmount : defaultRefillAmount;
        const fillPercentage = atm.currentAmount / refillAmount * 100;
        if (fillPercentage < 10) {
            return isAlert ? atmLowAlert : atmLow;
        } else if (fillPercentage < 50) {
            return isAlert ? atmMidAlert : atmMid;
        } else if (fillPercentage < 100) {
            return isAlert ? atmHighAlert : atmHigh;
        } else {
            return isAlert ? atmFullAlert : atmFull;
        }
    }

    private static isAlert(atm: Atm) {
        return atm.state !== "Operational";
    }

    public now = new Date();

    public state: State = {
        atms: [],
        config: {},
        default: {
            refillAmount: 100000,
            refillDelayHours: 5,
            load: 2,
            scheduledRefillInterval: 72,
        },
        editing: true,
        endDate: new Date(this.now.getFullYear(), this.now.getMonth(), this.now.getDate() + 7),
        endHour: this.now.getHours(),
        events: [],
        eventsPerHour: 100,
        interval: 1000,
        paused: false,
        playSpeed: 1,
        randomSeed: 1,
        selectedDate: this.now,
        selectedHour: this.now.getHours(),
        simulationName: "simulation",
        simulationTime: new Date(),
        startDate: this.now,
        startHour: this.now.getHours(),
        withdrawal: {
            distribution: "Gaussian",
            max: 10000,
            mean: 100,
            min: 10,
            stddev: 300
        },
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
            window.setTimeout(() => this.processEvents(events), 100);
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
        const atmBalances = {};
        const atmStates = {};
        const sideEffects: Props[] = [];
        events.forEach((event: Props) => {
            if (App.isSideEffect(event)) {
                event.id = `${event.atm}-${event.time}-${event.eventType}-${shortid.generate()}`;
                sideEffects.push(event)
            }

            const balance = "balance";
            if (event[balance] !== undefined) {
                if (atmBalances[event.atm] === undefined) {
                    atmBalances[event.atm] = [event]
                } else {
                    atmBalances[event.atm].push(event)
                }
            }

            const state = "state";
            if (event[state] !== undefined) {
                if (atmStates[event.atm] === undefined) {
                    atmStates[event.atm] = [event]
                } else {
                    atmStates[event.atm].push(event)
                }
            }
        });

        const atms = this.state.atms.map(atm => {
            const currentAtmBalances = atmBalances[atm.name];
            const currentAtmStates = atmStates[atm.name];

            if (currentAtmBalances !== undefined || currentAtmStates !== undefined) {
                const newAtm = {...atm};

                if (currentAtmBalances !== undefined) {
                    newAtm.currentAmount = currentAtmBalances[currentAtmBalances.length - 1].balance;
                }

                if (currentAtmStates !== undefined) {
                    newAtm.state = currentAtmStates[currentAtmStates.length - 1].state;
                }

                return newAtm;
            } else {
                return atm;
            }
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
                    <div>
                        <button onClick={this.decelerate}>-</button>
                        {this.state.paused ?
                            <button onClick={this.resume}>▶</button>
                            : <button onClick={this.pause}>||</button>}
                        <button onClick={this.accelerate}>+</button>
                    </div>
                </div>
            </div>
            <div>
                <a href={`${window.location.pathname}/log`}>Export simulation log</a>
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
            <div className="EditPanel-sectionLabel">
                General simulation settings
            </div>
            <div className="EditPanel-variable">
                Simulation name: <input type="string" name="simulationName"
                                        value={this.state.simulationName}
                                        onChange={this.simulationNameChanged}/>
            </div>
            <div className="EditPanel-variable">
                Start date: <input type="date" name="startDate"
                                   value={App.datepickerFormat(this.state.startDate)}
                                   onChange={this.startDateChanged}/>
            </div>
            <div className="EditPanel-variable">
                Start hour: <input type="number" name="startHour"
                                   min="0"
                                   max="24"
                                   value={this.state.startHour}
                                   onChange={this.startHourChanged}/>
            </div>
            <div className="EditPanel-variable">
                End date: <input type="date" name="endDate"
                                 value={App.datepickerFormat(this.state.endDate)}
                                 onChange={this.endDateChanged}/>
            </div>
            <div className="EditPanel-variable">
                End hour: <input type="number" name="endHour"
                                 min="0"
                                 max="24"
                                 value={this.state.endHour}
                                 onChange={this.endHourChanged}/>
            </div>

            <div className="EditPanel-variable">
                Random seed: <input type="number" name="endHour"
                                    value={this.state.randomSeed}
                                    onChange={this.randomSeedChanged}/>
            </div>
            <div className="EditPanel-sectionLabel">
                Default ATM settings
            </div>

            <div>Refill amount:
                <input type="number" name="refillAmount"
                       value={this.state.default.refillAmount}
                       onChange={this.defaultRefillAmountChanged}
                />
            </div>

            <div>Money refill interval in hours:
                <input type="number" name="scheduledRefillInterval"
                       value={this.state.default.scheduledRefillInterval}
                       onChange={this.defaultScheduledRefillIntervalChanged}
                />
            </div>
            <div>Load:
                <select name="load"
                        value={this.state.default.load}
                        onChange={this.defaultLoadChanged}
                >
                    <LoadOptions/>
                </select>
            </div>

            <div className="EditPanel-sectionLabel">
                Withdrawal settings
            </div>
            <div className="EditPanel-variable">
                Withdrawals per hour:
                <input type="number" name="eventsPerHour"
                       min="1"
                       max="10000"
                       value={this.state.eventsPerHour}
                       onChange={this.eventsPerHourChanged}/>
            </div>
            <div className="EditPanel-variable">
                Distribution of withdrawal amount:
                <select name="withdrawalDistribution"
                        value={this.state.withdrawal.distribution}
                        onChange={this.withdrawalDistributionChanged}>
                    {withdrawalDistributionTypes.map(type =>
                        <option key={type} value={type}>{type}</option>)}
                </select>
            </div>
            {this.state.withdrawal.distribution === withdrawalDistributionTypes[1]
                ? this.uniformDistributionParameters() :
                this.normalDistributionParameters()}

            <div className="EditPanel-sectionLabel"/>
            <div className="EditPanel-variable">
                <button onClick={this.startSimulation}>Start simulation</button>
            </div>
        </div>;
    }

    private normalDistributionParameters() {
        return <>
            <div className="EditPanel-variable">
                Mean withdrawal amount:
                <input type="number" name="withdrawalMean"
                       value={this.state.withdrawal.mean}
                       onChange={this.withdrawalMeanChanged}
                />
            </div>
            <div className="EditPanel-variable">
                Standard deviation of withdrawal amount:
                <input type="number" name="withdrawalStddev"
                       value={this.state.withdrawal.stddev}
                       onChange={this.withdrawalStddevChanged}
                />
            </div>
        </>;
    }

    private uniformDistributionParameters() {
        return <>
            <div className="EditPanel-variable">
                Minimum withdrawal amount:
                <input type="number" name="withdrawalMin"
                       value={this.state.withdrawal.min}
                       onChange={this.withdrawalMinChanged}
                />
            </div>
            <div className="EditPanel-variable">
                Maximum withdrawal amount:
                <input type="number" name="withdrawalMax"
                       value={this.state.withdrawal.max}
                       onChange={this.withdrawalMaxChanged}
                />
            </div>
        </>;
    }

    private loadConfig(simulationPath: string = "/default") {
        console.log(`Loading config: ${simulationPath}`);
        axios.get(`http://${server}/config${simulationPath}`,
            {headers: {Accept: "application/json"}})
            .then(r => {
                this.setState(s => {
                    const atms = r.data.atms.map(a => {
                        return {...a, currentAmount: a.refillAmount, state: "Operational"}
                    });
                    const startDate = new Date(r.data.startDate);
                    const startHour = startDate.getHours();
                    const endDate = new Date(r.data.endDate);
                    const endHour = endDate.getHours();
                    return {
                        ...s, atms, config: r.data, endDate, endHour, simulationTime: startDate,
                        startDate, startHour, withdrawal: r.data.withdrawal
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

    private randomSeedChanged = (e) => {
        console.log(`Random seed changed to ${e.target.value}`);
        const randomSeed = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, randomSeed});
    };

    private eventsPerHourChanged = (e) => {
        console.log(`Events per hour changed to ${e.target.value}`);
        const eventsPerHour = Number.parseInt(e.target.value, undefined);
        this.setState({...this.state, eventsPerHour});
    };

    private defaultRefillAmountChanged = (e) => {
        console.log(`Default refill amount changed to ${e.target.value}`);
        const refillAmount = Number.parseInt(e.target.value, undefined);
        const d = {...this.state.default, refillAmount};
        this.setState({...this.state, default: d});
    };

    private defaultScheduledRefillIntervalChanged = (e) => {
        console.log(`Default scheduled refill interval changed to ${e.target.value}`);
        const scheduledRefillInterval = Number.parseInt(e.target.value, undefined);
        const d = {...this.state.default, scheduledRefillInterval};
        this.setState({...this.state, default: d});
    };

    private defaultLoadChanged = (e) => {
        console.log(`Default load changed to ${e.target.value}`);
        const load = Number.parseInt(e.target.value, undefined);
        const d = {...this.state.default, load};
        this.setState({...this.state, default: d});
    };

    private withdrawalDistributionChanged = (e) => {
        console.log(`Withdrawal distribution changed to ${e.target.value}`);
        const withdrawal = {...this.state.withdrawal, distribution: e.target.value};
        this.setState({...this.state, withdrawal});
    };

    private withdrawalMinChanged = (e) => {
        console.log(`Withdrawal min changed to ${e.target.value}`);
        const min = Number.parseInt(e.target.value, undefined);
        const withdrawal = {...this.state.withdrawal, min};
        this.setState({...this.state, withdrawal});
    };

    private withdrawalMaxChanged = (e) => {
        console.log(`Withdrawal max changed to ${e.target.value}`);
        const max = Number.parseInt(e.target.value, undefined);
        const withdrawal = {...this.state.withdrawal, max};
        this.setState({...this.state, withdrawal});
    };

    private withdrawalMeanChanged = (e) => {
        console.log(`Withdrawal mean changed to ${e.target.value}`);
        const mean = Number.parseInt(e.target.value, undefined);
        const withdrawal = {...this.state.withdrawal, mean};
        this.setState({...this.state, withdrawal});
    };

    private withdrawalStddevChanged = (e) => {
        console.log(`Withdrawal stddev changed to ${e.target.value}`);
        const stddev = Number.parseInt(e.target.value, undefined);
        const withdrawal = {...this.state.withdrawal, stddev};
        this.setState({...this.state, withdrawal});
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
            <Marker key={a.name} position={a.location} icon={App.getIcon(a, this.state.default.refillAmount)}>
                <Popup>
                    <AtmPopup atm={a}
                              default={this.state.default}
                              editing={this.state.editing}
                              selectedDate={App.datepickerFormat(selectedDate)}
                              selectedHour={this.state.selectedHour}
                              timestamp={this.getTimestamp()}
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
                default: this.state.default,
                endDate,
                eventsPerHour: this.state.eventsPerHour,
                startDate,
                withdrawal: this.state.withdrawal,
                randomSeed: this.state.randomSeed
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
