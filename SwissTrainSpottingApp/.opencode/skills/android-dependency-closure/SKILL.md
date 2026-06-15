---
name: android-dependency-closure
description: Ensure all non-standard Android, AndroidX, test, or external symbols in SwissTrainSpottingApp have matching imports, version-catalog entries, correctly scoped Gradle dependencies in app/build.gradle, and a successful relevant build target.
---

# Android Dependency Closure — SwissTrainSpottingApp

## Purpose

Ensure that any newly introduced non-standard symbol in Android source code is fully wired before a task is considered complete.

This means:

- imports resolve
- the required dependency is declared in the version catalog and referenced from `app/build.gradle`
- the correct dependency scope is used
- the relevant build target compiles

## Core invariant

**Do not leave unresolved symbols introduced by your own changes.**

## Project layout

| Item | Location |
|------|----------|
| App module | `app/` |
| Version catalog | `gradle/libs.versions.toml` |
| Module dependencies | `app/build.gradle` |

This repo has a single Android application module (`app`). All new AndroidX, Android test, and third-party dependencies belong in the version catalog first, then in `app/build.gradle` via catalog aliases.

## What counts as non-standard

| Category | Examples | Required action |
|----------|----------|-----------------|
| AndroidX / Jetpack | `ExifInterface`, `ActivityResultLauncher`, `FileProvider` | import + catalog + `implementation` |
| UI libraries | `MaterialButton` | import + catalog + `implementation` |
| Test libraries | Espresso, JUnit, Intents | import + catalog + test scope |
| Third-party libraries | ONNX Runtime, ML Kit, parsing libs | import + catalog + `implementation` |
| Cross-package app code | another Activity or helper class | import only |

**Usually standard (no new dependency required):**
- `java.*`
- `javax.*`
- `android.*` SDK APIs already on the compile classpath
- same-package classes
- generated project symbols such as `R` and `BuildConfig`

## Import closure rules

1. Add explicit imports for all external symbols.
2. Do not rely on wildcard imports.
3. Do not rely on implicit resolution.
4. Use fully qualified names only when needed to resolve a name clash.
5. Prefer explicit imports over fully qualified class names in method signatures, fields, variable declarations, and normal code when there is no name clash.
6. Remove unused imports before finishing.
7. Ensure you are not mixing incompatible package families such as `android.*` and `androidx.*` variants for the same concept.

## Version catalog rules (required)

When a new dependency is needed:

1. **Search first** — check `gradle/libs.versions.toml` for an existing alias that already provides the artifact.
2. **Reuse if suitable** — if an alias exists, reference it from `app/build.gradle`; do not duplicate.
3. **Add to catalog if missing** — add a `[versions]` entry (when needed) and a `[libraries]` entry with the Maven coordinate.
4. **Reference from module** — in `app/build.gradle`, use `libs.<alias>` (e.g. `implementation libs.exifinterface`), not raw Maven coordinates.
5. **Do not bypass the catalog** — do not pin raw dependency strings in `app/build.gradle` when catalog ownership applies.

### Example flow (illustrative only)

Introducing `androidx.exifinterface.media.ExifInterface` in production code:

```toml
# gradle/libs.versions.toml
[versions]
exifinterface = "1.3.7"

[libraries]
exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exifinterface" }
```

```gradle
// app/build.gradle
dependencies {
    implementation libs.exifinterface
}
```

Introducing `androidx.test.espresso.intent.Intents` in instrumentation tests:

```toml
# gradle/libs.versions.toml — reuse espressoCore version if already present
[libraries]
espresso-intents = { group = "androidx.test.espresso", name = "espresso-intents", version.ref = "espressoCore" }
```

```gradle
// app/build.gradle
dependencies {
    androidTestImplementation libs.espresso.intents
}
```

These are examples, not an exhaustive list of allowed symbols.

## Scope rules

| Usage site | Scope in `app/build.gradle` |
|------------|----------------------------|
| Production app code (`app/src/main/`) | `implementation` |
| Local JVM unit tests (`app/src/test/`) | `testImplementation` |
| Instrumentation / Espresso / Android UI tests (`app/src/androidTest/`) | `androidTestImplementation` |

Use the narrowest correct scope. Test-only libraries must not appear as `implementation`.

## Pairing checklist

For every new external symbol you introduce:

```text
- [ ] Identify the owning Maven artifact
- [ ] Add the correct Java import
- [ ] Check gradle/libs.versions.toml for an existing alias; reuse if suitable
- [ ] If missing: add version + library entry to the catalog
- [ ] Add libs.<alias> to app/build.gradle with the correct scope
- [ ] Compile the relevant target (see below)
- [ ] Confirm no introduced symbol remains unresolved
```

## Build validation

Do not stop after editing source files or Gradle files.

From the `SwissTrainSpottingApp` module root:

| Change type | Minimum compile check |
|-------------|----------------------|
| Production code (`app/src/main/`) | `./gradlew :app:compileDebugJavaWithJavac` |
| Instrumentation tests (`app/src/androidTest/`) | `./gradlew :app:compileDebugAndroidTestJavaWithJavac` |
| Unit tests only (`app/src/test/`) | `./gradlew :app:compileDebugUnitTestJavaWithJavac` |
| Mixed production + test changes | compile all relevant targets |

Fix unresolved-symbol errors before declaring the task complete.

## Version handling

- Do not silently invent random versions.
- Reuse project-consistent versions when possible (e.g. align related AndroidX artifacts).
- Reuse existing catalog entries when possible.
- Do not upgrade unrelated dependencies.

## Minimality rules

- No unrelated dependency upgrades
- No speculative dependency additions
- No build-system redesign unless explicitly requested
- No migration away from the version catalog unless explicitly requested

## Failure patterns to avoid

Do not:

- add an import without the matching catalog entry and `app/build.gradle` reference when one is required
- add a catalog entry or Gradle dependency without the matching import when source code uses the symbol
- use the wrong scope (`implementation` vs `testImplementation` vs `androidTestImplementation`)
- pin raw Maven coordinates in `app/build.gradle` when the version catalog should own the dependency
- stop after source edits while the build still contains unresolved symbols introduced by your changes

## Completion rule

A change that introduces new external symbols is complete only when:

1. imports resolve
2. `gradle/libs.versions.toml` contains or reuses the correct entry
3. `app/build.gradle` references the catalog alias with the correct scope
4. the relevant build target compiles
5. no unresolved introduced symbols remain
