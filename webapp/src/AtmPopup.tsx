import * as React from 'react';


export default function AtmPopup(props) {
    return <div>
        <div>Name: {props.atm.name}</div>
        <div>Location: {props.atm.location[0]},{props.atm.location[1]}</div>
        <div>Refill amount: <input type="number" name="refillAmount"
                                   value={props.atm.refillAmount}
                                   onChange={props.refillAmountChanged}/>
        </div>
    </div>
}