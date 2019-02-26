import * as React from 'react';
import * as ReactDOM from 'react-dom';
import App from './App';
import './index.css';
import {initializeLeaflet} from "./leaflet-initialize"
import registerServiceWorker from './registerServiceWorker';

initializeLeaflet();
ReactDOM.render(
    <App/>,
    document.getElementById('root') as HTMLElement
);
registerServiceWorker();
