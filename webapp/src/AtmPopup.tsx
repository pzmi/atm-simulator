import * as React from 'react';
import {LoadOptions} from "./LoadOptions";

export default function AtmPopup(props) {
    const atmDefaultLoad = props.atm.atmDefaultLoad ? props.atm.atmDefaultLoad : props.default.load;
    const hourKey = props.timestamp.toString();
    const hourly = props.atm.hourly[hourKey];
    const hourlyLoadOption = hourly ? hourly.load : undefined;
    const hourlyLoad = hourlyLoadOption ? hourlyLoadOption : atmDefaultLoad;
    console.log(`hourly load is: ${hourlyLoad}`);
    return <div>
        <div>Name: {props.atm.name}</div>
        <div>Location: {props.atm.location[0]}, {props.atm.location[1]}</div>
        {props.editing ? <></> : <div>Current amount: {props.atm.currentAmount}</div>}
        <div>Refill amount: <input type="number" name="refillAmount"
                                   value={props.atm.refillAmount}
                                   placeholder={props.default.refillAmount}
                                   onChange={props.refillAmountChanged}
                                   disabled={!props.editing}/>
        </div>

        <div>Money refill interval in hours: <input type="number" name="scheduledRefillInterval"
                                                    value={props.scheduledRefillInterval}
                                                    placeholder={props.default.scheduledRefillInterval}
                                                    onChange={props.scheduledRefillIntervalChanged}
                                                    disabled={!props.editing}/>
        </div>
        {/*<div>Refill delay in hours: <input type="number" name="refillDelayHours"*/}
        {/*                                   value={props.atm.refillDelayHours}*/}
        {/*                                   placeholder={props.default.refillDelayHours}*/}
        {/*                                   onChange={props.refillDelayHoursChanged}*/}
        {/*                                   disabled={!props.editing}/>*/}
        {/*</div>*/}
        <div>ATM default load:
            <select name="atmDefaultLoad"
                    value={atmDefaultLoad}
                    onChange={props.atmDefaultLoadChanged}
                    disabled={!props.editing}>
                <LoadOptions/>
            </select>
        </div>
        <div>Date: <input type="date" name="selectedDate"
                          value={props.selectedDate}
                          onChange={props.selectedDateChanged}
                          disabled={!props.editing}/>
        </div>
        <div>Hour: <input type="number" name="selectedHour"
                          min="0"
                          max="24"
                          value={props.selectedHour}
                          onChange={props.selectedHourChanged}
                          disabled={!props.editing}/>
        </div>
        <div>Hourly load:
            <select
                name="hourlyLoad"
                value={hourlyLoad}
                onChange={props.hourlyLoadChanged}>
                <LoadOptions/>
            </select>
        </div>
    </div>
}