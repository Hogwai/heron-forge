import {defineWidget} from "../defineWidget";
import {DataTable} from "./DataTable";

export default defineWidget({
    type: "table",
    slot: "wide",
    component: DataTable,
});
