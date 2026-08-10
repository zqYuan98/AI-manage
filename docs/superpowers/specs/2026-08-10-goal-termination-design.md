# Goal termination and archive design

## Problem

The goal page exposes a disabled delete button for every non-draft goal. The database and the approved authorization matrix already define `TERMINATED` and allow the department manager to terminate goals, but no endpoint or UI action implements that lifecycle transition.

## Decision

- A `DRAFT` goal remains physically hidden by the existing logical-delete operation, and only when it has no child goals or linked tasks.
- An `ACTIVE` goal is never deleted. A department manager with a dedicated `lab:goal:terminate` permission may terminate it with a required 5–500 character reason.
- `COMPLETED` and `TERMINATED` goals are immutable terminal records.
- Termination preserves linked tasks, performance facts, reports, and audit history.
- A linked monthly task is settled only when its workflow is `CONFIRMED`. A linked weekly commitment is settled only when its execution state is `SELF_DONE`, `SELF_UNDONE`, or `CANCELLED`. Any other linked task blocks termination and the service returns the unresolved count.
- Terminating an annual goal also terminates its non-terminal quarterly children after all linked tasks are settled. Completed and already-terminated children are preserved as-is.
- The transition stores reason, actor, and time in dedicated goal columns and uses optimistic locking.
- The goal page defaults to active goals, offers filters for draft/completed/terminated/all, shows a delete explanation, and provides a manager-only “terminate and archive” action. Terminated records remain discoverable through the terminated/all filters.

## Alternatives rejected

1. Cascade-delete an active goal and its tasks: this destroys the evidence used by performance and reports.
2. Add only a visual hidden/archive flag: this creates a second lifecycle that can disagree with the existing `TERMINATED` status.
3. Terminate while linked tasks are still active: this leaves executable work attached to a closed strategic objective.

## Verification

- Unit contracts cover role, state, reason, optimistic version, task settlement, child cascade, and audit fields.
- Controller and SQL contracts cover the dedicated permission and endpoint.
- Mapper XML is parsed and contains no caller-expanded SQL.
- Targeted backend tests, full `ruoyi-lab` tests, SQL verifier, frontend lint/build, and admin package must pass before completion is claimed.
