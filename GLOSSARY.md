# e4eHeHWorkout

A personal Android workout tracker for logging training across multiple disciplines and visualizing long-term progression.

## Language

**Discipline**:
A top-level training category (`Calisthenics`, `Conditioning`, `Cardio`, `Weight Lifting`) used to classify Routines and Sessions.
_Avoid_: Workout type, category

**Exercise**:
A single movement or activity (such as Pull-ups, L-sit, 5K Run, or EMOM Circuit) with a defined Tracking Mode.
_Avoid_: Move, drill

**Tracking Mode**:
The measurement model an Exercise uses to record performance: Reps & Load, Hold/Duration, Distance & Time, or Conditioning/Rounds.
_Avoid_: Metric type, input type

**Routine**:
A reusable workout plan composed of ordered Exercises under one primary Discipline (such as "Calisthenics Push" or "Weekly Conditioning").
_Avoid_: Template, workout plan, preset

**Session**:
An independent snapshot of a single logged workout performed on a specific date and time, recording actual Sets, duration, and optional notes under one primary Discipline.
_Avoid_: Workout log, entry, activity

**Set**:
A single recorded effort for an Exercise within a Session, capturing the measurement defined by that Exercise's Tracking Mode.
_Avoid_: Rep block, row

**Working Set**:
A Set that counts toward Personal Records and progression charts.
_Avoid_: Main set

**Warm-up Set**:
A Set marked as preparation that is kept in Session history but excluded from Personal Records and progression metrics.
_Avoid_: Prep set

**Personal Record (PR)**:
The highest recorded performance for an Exercise across all completed Sessions (such as max reps, heaviest load, or longest hold).
_Avoid_: High score, best, PB

**Bodyweight Entry**:
A dated personal bodyweight measurement tracked independently from Exercise load.
_Avoid_: Weight log, mass
