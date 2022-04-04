import { Environment } from "src/environments";

export const environment: Environment = {
    theme: "OpenEMS",

    uiTitle: "OpenEMS UI",
    edgeShortName: "OpenEMS",
    edgeLongName: "Open Energy Management System",

    backend: 'OpenEMS Backend',
    url: (location.protocol == "https:" ? "wss" : "ws") +
        "://" + location.hostname + ":" + location.port + "/openems-backend-ui2",

    production: true,
    debugMode: false,
};
