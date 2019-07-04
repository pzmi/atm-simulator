import * as React from "react";

const loadTypes = {
    high: {
        label: "High",
        value: 5
    },
    medium: {
        label: "Medium",
        value: 3
    },
    low: {
        label: "Low",
        value: 2
    },
    veryLow: {
        label: "Very low",
        value: 1
    },
    off: {
        label: "Off",
        value: 0
    }
};

function LoadOptions(props) {
    return <>
        {Object.values(loadTypes)
            .map(loadType =>
                <option key={loadType.value} value={loadType.value}>
                    {loadType.label}
                </option>
            )
        }
    </>;
}

export {loadTypes, LoadOptions}