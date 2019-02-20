import * as React from 'react';
import * as ReactDOM from 'react-dom';
import App from './App';
import './index.css';
import {initialize} from "./leaflet-initialize"
import registerServiceWorker from './registerServiceWorker';

initialize();
ReactDOM.render(
    <App/>,
    document.getElementById('root') as HTMLElement
);
registerServiceWorker();
