import * as React from 'react';


export default function AtmPopup(props) {
    return <div>
        <div>Name: {props.atm.name}</div>
        <div>Location: {props.atm.location[0]},{props.atm.location[1]}</div>
        <div>Refill amount: <input type="number" name="refillAmount"
                                   value={props.atm.refillAmount}
                                   placeholder={props.default.refillAmount}
                                   onChange={props.refillAmountChanged}/>
        </div>
        <div>Refill delay in hours: <input type="number" name="refillDelayHours"
                                           value={props.atm.refillDelayHours}
                                           placeholder={props.default.refillDelayHours}
                                           onChange={props.refillDelayHoursChanged}/>
        </div>
        <div>ATM default load: <input type="number" name="atmDefaultLoad"
                                      value={props.atm.atmDefaultLoad}
                                      placeholder={props.default.load}
                                      onChange={props.atmDefaultLoadChanged}/>
        </div>
        <div>Date: <input type="date" name="selectedDate"
                          value={props.selectedDate}
                          onChange={props.selectedDateChanged}/>
        </div>
        <div>Hour: <input type="number" name="selectedHour"
                          min="0"
                          max="24"
                          value={props.selectedHour}
                          onChange={props.selectedHourChanged}/>
        </div>
        <div>Hourly load: <input type="number" name="hourlyLoad"
                                 value={props.hourlyLoad}
                                 onChange={props.hourlyLoadChanged}/>
        </div>
    </div>
}