# Android Dependency Closure — Project Adaptation Prompt

## Purpose

This file is **not** the canonical skill itself.
This file is a **project-local adaptation prompt**.

Use it to transform the generic `android-dependency-closure` skill template into an actionable repo-specific skill for the current Android project.

## How to use

When generating or updating a tool-specific skill artifact:

1. Read the generic dependency-closure skill; the canonical location is doc/agent-skills/android-dependency-closure/android-dependency-closure.generic.md
2. Inject the project-specific facts from this prompt and your repo analysis into the generic skill.
4. Produce a sharpened tool-facing skill artifact for the current repository.
5. Do not weaken the generic closure rules.
6. Do not replace generic rules with project examples only.
7. Output location: in your project-specific cache folder, i.e. .opencode/skills/android-dependency-closure/SKILL.md when running for/from OpenCode, .cursor/skills/android-dependency-closure/SKILL.md when running for/from Cursor and analogously for other agentic systems.

## Adaptation task

Take the generic `android-dependency-closure` skill and adapt it to this repository.

### Preserve from the generic skill
Keep the following intact unless there is a strong project-specific reason to narrow wording:

- the core invariant: do not leave unresolved symbols introduced by your own changes
- explicit import closure
- dependency closure
- correct scope selection
- respect for the repository's dependency-management style
- build-target verification before completion
- minimality / no unrelated dependency upgrades
- failure patterns to avoid

### Inject project-local facts
Sharpen the generic skill using the following repository facts:

- this repository contains an Android app module named `app`
- this repository uses a version catalog at `gradle/libs.versions.toml`
- new AndroidX, Android test, and third-party dependencies should normally be added through the version catalog
- `app/build.gradle` should normally reference catalog aliases rather than raw Maven coordinates when catalog ownership applies
- production Android code should use `implementation`
- local JVM tests should use `testImplementation`
- instrumentation / Espresso / Android UI tests should use `androidTestImplementation`
- reuse an existing catalog alias if a suitable alias already exists
- do not add raw pinned dependency strings in `app/build.gradle` when the version catalog should own the dependency

### Make the result more actionable for this repo
The adapted artifact should explicitly help the agent avoid the failure mode:

- symbol is introduced in Java or test code
- import may or may not be added
- dependency declaration in the version catalog or Gradle file is forgotten
- task is declared complete while the build is still broken

### Add repo-local examples only as examples
You may include examples such as:

- `androidx.exifinterface.media.ExifInterface`
- `androidx.test.espresso.intent.Intents`

but examples must remain examples, not the entire rule.

Do not overfit the adapted artifact to current single-use cases.

## Output requirements

The adapted final SKILL.md artifact should:

- remain recognizably based on the generic skill
- become more operational in this repository
- mention the version catalog explicitly
- mention `app/build.gradle` explicitly
- mention correct dependency scope explicitly
- include a short checklist for the agent
- stay concise enough to be practically usable by the target tool

## What not to do

Do not:

- turn the artifact into a list of only current project examples
- hardcode every dependency alias currently present in the repository
- replace generic rules with SwissTrainSpotting-specific trivia
- remove build verification
- remove the no-unresolved-symbol invariant
- weaken catalog discipline into optional advice

## Success criterion

A successful adapted artifact should make an agent do the following reliably:

1. introduce a new AndroidX or test symbol
2. add the correct import
3. add or reuse the correct catalog entry
4. reference it from `app/build.gradle` with the correct scope
5. compile the relevant target before stopping
