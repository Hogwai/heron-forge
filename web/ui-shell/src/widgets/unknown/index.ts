import {defineWidget} from "../defineWidget";
import {UnknownWidget} from "./UnknownWidget";

export default defineWidget({
    type: "unknown",
    slot: "wide",
    component: UnknownWidget,
});
