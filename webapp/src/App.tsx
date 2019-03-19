import * as React from 'react';
// @ts-ignore
import {Map, Marker, Popup, TileLayer} from "react-leaflet";
import './App.css';
import {Event, Props} from './Event'

interface State {
    lat: number,
    lng: number,
    zoom: number
    events: Props[]
}

class App extends React.Component<any, State> {
    public state: State = {
        events: [],
        lat: 50.059683,
        lng: 19.944544,
        zoom: 14
    };

    public componentDidMount(): void {
        const websocket = new WebSocket("ws://localhost:8080/websocket");
        websocket.onmessage = (m) => this.setState((s) => {
            return {...s, events: [JSON.parse(m.data), ...s.events]}
        });
    }

    public render() {
        const position = [this.state.lat, this.state.lng];
        return (
            <div className="App">
                <div className="map-with-events">
                    <Map center={position} zoom={this.state.zoom}>
                        <TileLayer
                            attribution='&amp;copy <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />
                        <Marker position={position}>
                            <Popup>
                                A pretty CSS3 popup. <br/> Easily customizable.
                            </Popup>
                        </Marker>
                    </Map>
                    <div className="events">
                        {this.state.events
                            .map((m: Props, i) => <Event key={i} eventData={m}/>)
                        }
                    </div>
                </div>
            </div>
        );
    }
}

export default App;
