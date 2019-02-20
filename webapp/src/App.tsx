import * as React from 'react';
import './App.css';

// @ts-ignore
import {Map, Marker, Popup, TileLayer} from "react-leaflet";
import logo from './logo.svg';

interface State {
    lat: number,
    lng: number,
    zoom: number
}

class App extends React.Component<any, State> {
    public state: State = {
        lat: 50.059683,
        lng: 19.944544,
        zoom: 14,
    };

    public render() {

        const position = [this.state.lat, this.state.lng];
        return (
            <div className="App">
                <header className="App-header">
                    <img src={logo} className="App-logo" alt="logo"/>
                    <h1 className="App-title">Welcome to React</h1>
                </header>
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
            </div>
        );
    }
}

export default App;
