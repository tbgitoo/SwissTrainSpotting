# Module 7 — Finalization (Documentation, Structure Review, Packaging Readiness)

**Status:** Working implementation plan  
**Depends on:** Modules 1–5 complete; Module 6 optional  
**Reference:** `plans/01_architecture.md`, `SwissTrainSpottingApp/AGENTS.md`, `model/AGENTS.md`  
**Target:** Project-wide (root level), covering `model/` and `SwissTrainSpottingApp/`

---

## Purpose

Close the project in a reusable and installable state.

Module 7 does not add features. It focuses on:
- lightweight documentation for future reuse
- structural review of the current code layout
- Android packaging readiness and manual APK installation

---

## Scope

### Included
- JavaDoc on important Android public classes
- Python docstrings on main training/export scripts
- proposal-level structure review
- manual APK packaging/install checklist

### Not included
- major refactoring by agent
- automated packaging pipeline
- Play Store publishing
- Python deployment packaging
- new functionality

---

## Inputs / outputs

### Inputs
- working Android app
- working Python training/export pipeline
- existing plans and READMEs
- existing tests

### Outputs
- concise JavaDoc / Python docstrings
- `plans/module_07/structural_review_checklist.md`
- `plans/module_07/apk_packaging_checklist.md`

---

## Architectural placement

Module 7 is a root-level finalization module:
- **Python side:** documentation for reuse
- **Android side:** structure review and APK readiness

The Android app is the deployable artifact.  
The Python side is documented for future local reuse, not packaged.

---

## Step-by-step workflow

### Phase 7A — Documentation
Add concise documentation where it is most useful.

**Android**
- document key public classes and interfaces
- focus on responsibilities, important inputs/outputs, and non-obvious behavior
- do not document every trivial private helper

**Python**
- add module docstrings to main scripts
- add docstrings to important public functions
- focus on training/export workflow and profile usage

**Goal:** improve readability and reuse without changing behavior.

---

###Phase 7B — Structural review

Review whether the current layout clearly reflects functional responsibilities and remains understandable for future work.

Improvements focus on:

- aligning Java packages with coherent functional units (e.g. preprocessing, inference, OCR, UI)
- regrouping tests by module/phase with consistent, behavior-oriented naming
- updating existing plans conceptually to a v2 state to reflect the current, refactored structure

Changes are applied through custom prompts, or if necessary manually in Android Studio where they improve clarity.

Default assumption: keep current structure unless cleanup clearly adds value.

---

### Phase 7C — Packaging readiness
Prepare manual Android packaging and installation.

Produce:
- `plans/module_07/apk_packaging_checklist.md`

Checklist should contain:
- verify app builds in Android Studio
- verify required assets/profile are present
- run a short smoke test on device or emulator
- manually build APK in Android Studio
- install APK on target device
- record whether install and smoke test succeeded

Packaging is manual and Android Studio–driven.  
No command-line release pipeline is required here.

---

## Testing / validation

Module 7 should not introduce new behavior.

Validation is limited to:
- existing tests still pass after documentation changes
- app still builds after any manual cleanup
- APK installs and basic smoke scenarios work

---

## Done when

- important Android classes have concise JavaDoc
- main Python scripts have concise docstrings
- `structural_review_checklist.md` exists
- `apk_packaging_checklist.md` exists
- Android app builds and installs manually from Android Studio
- manual smoke test passes
- no false claim of restructuring unless it was actually done manually

