# Workout flow experiment

Isolated branch: `fork/workout-flow`. Do not merge or deploy production until requested.

The debug application installs as `com.fitclub.tracker.experimental` (FitClub · Тест), alongside the production app. It has its own database and settings. Before a future production deployment, remove the experimental application ID suffix / name override and assign the next production version code.

Implemented:

- Selecting a calendar date shows every scheduled and completed session. Planning requires an explicit action; a second plan is always a new ID.
- Long-press a plan or completed-session card to request deletion, then confirm or cancel.
- View completed-session sets, exercise timing, weight records and total workout tonnage. Edit recorded weights/reps, completion flags, sets and total duration; save replaces that session, rather than creating a duplicate.
- Uniform 56 dp primary/secondary actions, including equal-size footer actions.
- Catalog thumbnails in exercise cards, improved program-choice spacing.
- Starting a workout opens its exercise overview. Open any exercise and return without losing values, completed sets, elapsed exercise time or status.
- Hold the drag handle to reorder. Arrows provide an alternative. Add exercises or confirm removal (removal discards that exercise's inputs). Logs and completion remain attached to the correct exercise, even for repeated exercise IDs.
- On completion, save the modified plan and performed results. Unchecked sets and timed sets are excluded from weight tonnage.
- Room migration 5 → 6 replaces the unique date index with a normal index; existing records are preserved.

Validation includes migration from versions 1–5, same-date plan isolation, session editing/deletion, UI navigation and arbitrary exercise selection, dragging and preservation of inputs, and native smartphone previews.
