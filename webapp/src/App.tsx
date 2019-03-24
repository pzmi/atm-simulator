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
}

const cracowLocation = [50.06143, 19.944544];

const icon = L.icon({
    iconRetinaUrl: notesBig,
    iconSize: [48, 48], // size of the icon,
    iconUrl: notes
});

class App extends React.Component<any, State> {

    public state: State = {
        atms: [],
        events: [],
        zoom: 14
    };

    public componentDidMount(): void {
        this.loadAtms();
        const websocket = new WebSocket("ws://localhost:8080/websocket");
        websocket.onmessage = (m) => this.setState((s) => {
            return {...s, events: [JSON.parse(m.data), ...s.events]}
        });
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

    private loadAtms() {
        axios.get('static/atms.json')
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
