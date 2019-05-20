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
    selectedDate: Date
    selectedHour: number
    interval: number
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

    public state: State = {
        atms: [],
        config: {},
        events: [],
        interval: 10,
        selectedDate: new Date(),
        selectedHour: new Date().getHours(),
        zoom: 14
    };

    private websocket;

    private handleStartSimulation = this.startSimulation.bind(this);

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
                            <button onClick={this.handleStartSimulation}>Start simulation</button>
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
                window.setTimeout(() => this.addToSideEffectsBox(events, index + 1), this.state.interval);
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

    private selectedDateChanged() {
        return e => {
            console.log(`Selected date changed to ${e.target.value}`);
            const selectedDate = new Date(e.target.value);
            this.setState({...this.state, selectedDate})
        }
    }

    private selectedHourChanged() {
        return e => {
            console.log(`Selected hour changed to ${e.target.value}`);
            const selectedHour = e.target.value;
            this.setState({...this.state, selectedHour});
        }
    }

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
            this.setState({...this.state, atms})
        }
    }

    private atms() {
        const selectedDate = this.state.selectedDate;

        return this.state.atms.map((a) =>
            <Marker key={a.name} position={a.location} icon={icon}>
                <Popup>
                    <AtmPopup atm={a}
                              default={this.state.config.default}
                              selectedDate={dateFormat(selectedDate, "yyyy-mm-dd")}
                              selectedHour={this.state.selectedHour}
                              refillAmountChanged={this.refillAmountChanged(a)}
                              refillDelayHoursChanged={this.refillDelayHoursChanged(a)}
                              atmDefaultLoadChanged={this.atmDefaultLoadChanged(a)}
                              selectedDateChanged={this.selectedDateChanged()}
                              selectedHourChanged={this.selectedHourChanged()}
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

    private startSimulation() {
        axios.post(`http://${server}/simulation/a.log`,
            {
                atms: this.state.atms,
                default: this.state.config.default,
                withdrawal: this.state.config.withdrawal,
            })
            .then(response => console.log(`Simulation response ${response}`))
            .catch(errorResponse => `Simulation error ${errorResponse}`)
    }
}

export default App;
