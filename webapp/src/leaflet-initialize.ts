import * as  L from 'leaflet';
import 'leaflet/dist/leaflet.css';
/* This code is needed to properly load the images in the Leaflet CSS */

export function initialize() {
    delete L.Icon.Default.prototype._getIconUrl;
    L.Icon.Default.mergeOptions({
        iconRetinaUrl: require('leaflet/dist/images/marker-icon-2x.png'),
        iconUrl: require('leaflet/dist/images/marker-icon.png'),
        shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
    });
}


