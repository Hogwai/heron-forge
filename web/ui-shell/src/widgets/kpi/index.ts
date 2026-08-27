import {defineWidget} from "../defineWidget";
import {KpiCard} from "./KpiCard";

export default defineWidget({
    type: "kpi",
    slot: "compact",
    component: KpiCard,
});
