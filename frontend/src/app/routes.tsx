import { createBrowserRouter } from "react-router";
import Root from "./components/Root";
import Step1Input from "./components/Step1Input";
import Step2Report from "./components/Step2Report";
import Step3Cases from "./components/Step3Cases";
import Step4Checklist from "./components/Step4Checklist";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: Step1Input },
      { path: "report", Component: Step2Report },
      { path: "cases", Component: Step3Cases },
      { path: "checklist", Component: Step4Checklist },
    ],
  },
]);
