import { defineWidget } from "../defineWidget";
import { RatioChart } from "./RatioChart";

export default defineWidget({
  type: "chart",
  slot: "medium",
  component: RatioChart,
});
