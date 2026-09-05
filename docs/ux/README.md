# UX design

Status: discovery in progress.

This directory tracks the UI and UX redesign on branch `ux`. Documentation is written in English; discovery conversations may be in Italian.

## Working documents

- [Live simplification experiment](live-simplification.md): rejected alternative, retained for reference. The active prototype is `wireframes/prototype.html`.
- [Connected prototype](prototype.md): entry point, implemented navigation paths, review choices, and verification. The current integrated preview is `wireframes/prototype.html`.
- [Brief](brief.md): confirmed requirements, candidate features, and open questions.
- [User scenarios](scenarios.md): usage contexts, tasks, and proposed success criteria.
- [Information architecture proposal](architecture.md): draft concepts, information groups, and task sequences for discussion.
- [History overview](history-overview.md): approved overview/detail direction, with finer interactions and tradeoffs for review.
- [Live overview](live-overview.md): confirmed measurement visibility and a first content-layout proposal in `wireframes/live.html`.
- [Sensor registration](registration.md): local metadata requirements and the registration flow under discussion.
- [Navigation and task flows](navigation.md): confirmed destinations, proposed entry points, and unresolved capability/empty states.
- [History wireframe](wireframes/history.html): interactive mockup with illustrative data, opening in the portrait overview with a simulated automatic update. Open a sensor, then drag data beneath the fixed central cursor to inspect samples; pinch or use the mouse wheel to zoom around it. An overlaid icon toggles comparison. Return to the portrait overview to use its time window control. Automatic overview updating simulates a partial result without contacting devices.
- [Current state](current-state.md): implementation observations relevant to design, not target behavior.
- [Decisions](decisions.md): agreed decisions and their rationale.

## Workflow

1. Establish users, contexts, and priority tasks through concrete examples.
2. Agree on information architecture and task flows, including failure paths.
3. Create wireframes for the priority flow and its relevant states.
4. Walk through a prototype and record validation observations.
5. Define visual components and implementation acceptance criteria.

Create additional artifacts when their content is ready. Keep confirmed requirements separate from proposals and unanswered questions. Link future flows, screens, and implementation tasks to requirement IDs. Record changed decisions with their rationale rather than silently replacing the history.

The existing implementation is evidence about current behavior, not an approved design for the redesign.
