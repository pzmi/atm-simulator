import axios from 'axios'
import * as  L from 'leaflet';
import * as React from 'react';
// @ts-ignore
import {Icon, Map, Marker, Popup, TileLayer} from "react-leaflet";
import './App.css';
import {Event, Props} from './Event'
import notesBig from './notes-x2.png'
import notes from './notes.png'

interface Atm {
    location: number[]
    name: string
}

interface State {
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

class App extends React.Component<any, State> {

    private static isSideEffect(m) {
        return sideEffects.includes(m.eventType);
    }

    public state: State = {
        atms: [],
        events: [],
        interval: 10,
        zoom: 14
    };

    private websocket;

    public componentDidMount(): void {
        this.loadAtms();
        this.websocket = new WebSocket("ws://localhost:8080/websocket/output");
        this.websocket.onopen = () => this.websocket.send("hello");
        this.websocket.onmessage = (m) => {
            if (m.data === "ping") {
                console.log("ping")
            } else {
                const events = JSON.parse(m.data);
                const sideEffectEvents = events.filter(e => App.isSideEffect(e));
                this.addTosideEffectsBox(sideEffectEvents, 0);

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

    private addTosideEffectsBox(events, index: number) {
        window.setTimeout(() => {
            this.setState((s) => {
                return {...s, events: [events[index], ...s.events]}
            });
            if (index < events.length - 1) {
                window.setTimeout(() => this.addTosideEffectsBox(events, index + 1), this.state.interval);
            } else {
                this.websocket.send("Batch finished")
            }
        }, this.state.interval);
    }

    private loadAtms() {
        axios.get('config')
            .then(r => {
                const atms = r.data.atms;

                this.setState(s => {
                    return {...s, atms}
                })
            });
    }

    private atms() {
        return this.state.atms.map((a) =>
            <Marker key={a.name} position={a.location} icon={icon}>
                <Popup>
                    Atm: {a.name}
                </Popup>
            </Marker>
        );
    }
}

export default App;
