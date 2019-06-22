import * as React from "react";

export interface Props {
    eventType: string,
    time: number,
    atm: string
    amount: number,
    balance: number,
    id?: string
}

export interface EventData {
    eventData: Props
}

export function Event(eventData: EventData) {
    const data = eventData.eventData;
    const eventHeader = data.eventType.charAt(0).toUpperCase() + data.eventType.substr(1);
    const eventDate = new Date(data.time).toLocaleString();
    return <div className="Event-container">
        <div className="Event-header">
            {eventHeader}
        </div>
        <div>
            {part("Atm", data.atm)}
            {part("Time", eventDate)}
        </div>
    </div>
}

function part(name: string, prop: string | number) {
    if (part !== null) {
        return <div>{name}: {prop} </div>
    } else {
        return <></>
    }
}