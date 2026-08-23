# Chart analytics roadmap for Satzwerk

## Goal

Expand Satzwerk's analytics from summary widgets into a phased chart roadmap that can incorporate the most valuable chart features seen in openGym without forcing one large redesign.

## Current state in Satzwerk

Satzwerk already has a useful summary layer:

- a GitHub-style **Heatmap**
- a **WeeklyTrendChart** for sets per week
- "Most trained" and "Least trained" exercise summaries
- **WorkoutSession** history with expandable set detail
- per-exercise reference data during training, including previous weight, PR, estimated 1RM range, and suggested weight

This is good for quick answers, but weak for deeper trend analysis. A user can see that they trained, but not inspect how one **Exercise** is progressing over time the way openGym allows.

## Reference behavior from openGym

openGym treats charts as a dedicated analytics surface, not just dashboard decoration.

Its analytics break into distinct families:

1. exercise progress curves
2. body-weight trend charts
3. richer activity views around the heatmap
4. muscle distribution analytics
5. effort analytics

The important product idea is not "copy every chart." It is "let the user drill into one question at a time."

## Recommended breakdown

Satzwerk should split chart work into five independent analytics families.

### 1. Exercise progress curves

Purpose: help the user answer, "Is this **Exercise** progressing?"

Scope:

- choose one **Exercise**
- show top-set trend over completed **WorkoutSession** history
- optionally overlay estimated 1RM
- show recent logged set context beside the chart

Why first:

- Satzwerk already has the required **SetLog** history
- Satzwerk already computes estimated 1RM ranges and suggested weight hints
- this is the highest-value chart family with the lowest data-model risk

### 2. BodyMeasurement trends

Purpose: show whether body metrics are moving in the intended direction over time.

Scope:

- weight-over-time chart
- goal line
- simple period deltas
- later expansion to other **BodyMeasurement** fields

Why second:

- Satzwerk already has **BodyMeasurement**
- the data model already exists, so the main work is analytics presentation

### 3. Richer activity views

Purpose: deepen the existing **Heatmap** instead of replacing it.

Scope:

- keep the current set-count **Heatmap**
- add alternate metric views such as workout frequency or session duration
- improve drill-down from a day cell into the underlying **WorkoutSession** data

Why third:

- Satzwerk already has a strong activity entry point
- users get more value by extending it than by rebuilding it

### 4. Muscle distribution analytics

Purpose: show which muscle groups are receiving training attention and which are being neglected.

Scope:

- start with summary aggregation by muscle group
- optionally evolve into a visual body map later

Why fourth:

- this needs more aggregation design than the earlier slices
- a summary-first version validates the model before adding a more complex visual body diagram

### 5. Effort analytics

Purpose: show training difficulty, not just training volume.

Scope:

- trend of effort over time
- effort distribution view
- hard-set summary

Why last:

- Satzwerk does not currently store RIR/RPE-like effort data in **SetLog**
- the chart layer should not arrive before the data model exists

## Recommended first target

The first implementation target should be **Exercise progress curves**.

The first slice should emphasize:

- top-set progression
- optional estimated 1RM overlay

It should not attempt full openGym parity on day one. Effort views, muscle balance, and broader analytics navigation can follow after the first drill-down experience proves useful.

## Product approach

Use an analytics-hub-first approach.

That means:

- keep the dashboard summary-oriented
- add a dedicated analytics drill-down surface for deeper chart inspection
- avoid overloading the dashboard with too many dense charts

This matches the difference between the products:

- Satzwerk's current dashboard is for quick orientation
- openGym's stats experience is for interactive inspection

Satzwerk can adopt the deeper inspection model without losing the simplicity of its current dashboard.

## How Satzwerk can include the openGym-style chart features

### Exercise progress

Add a dedicated analytics surface where the user selects an **Exercise** and sees:

- top-set trend by completed **WorkoutSession**
- optional estimated 1RM overlay
- recent **SetLog** context

This should reuse existing **WorkoutSession** and **SetLog** history rather than inventing a new domain object.

### BodyMeasurement charts

Anchor these charts to **BodyMeasurement**, not to the workout dashboard.

This keeps chart ownership aligned with the domain and avoids mixing training analytics with body-composition analytics in one crowded surface.

### Richer activity analytics

Extend the current **Heatmap** rather than replacing it.

The existing activity view is already a good entry point. The next step is more metric options and better drill-down, not a brand-new activity visualization model.

### Muscle distribution

Start with derived summaries from **Exercise** muscle-group metadata and **WorkoutExercise** volume. Only move to a body map after the aggregation model is trusted.

### Effort analytics

Delay this until Satzwerk records effort in **SetLog**. Otherwise the product would ship a chart family with no reliable source data.

## Phased roadmap

### Slice 1: Exercise progress hub

- exercise selector
- top-set curve
- optional estimated 1RM overlay
- recent **WorkoutSession** context

### Slice 2: BodyMeasurement trends

- body weight chart
- goal line
- period deltas

### Slice 3: Heatmap expansion

- alternate metrics
- stronger drill-down from the **Heatmap**

### Slice 4: Muscle distribution summary

- muscle-group aggregation
- coverage and imbalance summaries

### Slice 5: Effort analytics

- requires future **SetLog** effort data
- trend and distribution views after the model exists

## Validation

The roadmap is successful if:

- each slice is useful on its own
- the first slice lets a user answer "Is this **Exercise** progressing?" without reading raw **WorkoutSession** history
- later slices reuse a common analytics navigation model instead of creating separate disconnected dashboards
- the roadmap respects current domain boundaries such as **WorkoutSession**, **SetLog**, **Heatmap**, and **BodyMeasurement**

## Non-goals

- rebuilding the current dashboard all at once
- copying openGym's full analytics surface in one release
- adding effort charts before effort data exists
- introducing new domain entities when existing Satzwerk entities already support the slice
