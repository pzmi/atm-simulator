import axios from 'axios'
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
    readonly config: object
    atms: Atm[]
    events: Props[]
    zoom: number
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
                    <div className="Events">
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
        axios.get(`http://${server}/config`)
            .then(r => {
                this.setState(s => {
                    return {...s, atms: r.data.atms, config: r.data}
                });
            });
    }

    private refillAmountChanged(atm) {
        return e => {
            const atms = this.state.atms.map(x => {
                if (x.name === atm.name) {
                    return {
                        ...atm,
                        refillAmount: Number.parseInt(e.target.value, undefined)
                    };
                } else {
                    return x
                }
            });
            this.setState({...this.state, atms})
        }
    }

    private atms() {
        return this.state.atms.map((a) =>
            <Marker key={a.name} position={a.location} icon={icon}>
                <Popup>
                    <AtmPopup atm={a} refillAmountChanged={this.refillAmountChanged(a)}/>
                </Popup>
            </Marker>
        );
    }
}

export default App;
