import { createBrowserRouter, Navigate } from "react-router";
import AuthPage from "./components/AuthPage";
import ProtectedRoute from "./components/ProtectedRoute";
import Root from "./components/Root";
import Step1Input from "./components/Step1Input";
import Step2Report from "./components/Step2Report";
import Step3Cases from "./components/Step3Cases";
import Step4Checklist from "./components/Step4Checklist";
import LawAssistantPage from "./components/LawAssistantPage";
import AccidentResponsePage from "./components/AccidentResponsePage";

export const router = createBrowserRouter(
  [
    { path: "/login", Component: AuthPage },
    {
      Component: ProtectedRoute,
      children: [
        {
          path: "/",
          Component: Root,
          children: [
            { index: true, Component: Step1Input },
            { path: "report", element: <Navigate to="/checklist" replace /> },
            { path: "cases", Component: Step3Cases },
            { path: "checklist", Component: Step4Checklist },
            { path: "prevention", Component: Step2Report },
            { path: "laws", Component: LawAssistantPage },
            { path: "accident-response", Component: AccidentResponsePage },
          ],
        },
      ],
    },
  ],
  { basename: import.meta.env.BASE_URL },
);
