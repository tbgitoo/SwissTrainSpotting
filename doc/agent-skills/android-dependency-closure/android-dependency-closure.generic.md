---
name: android-dependency-closure
description: Ensure all non-standard Android, AndroidX, test, or external symbols have matching imports, correctly scoped Gradle dependencies, and a successful relevant build target.
---

# Android Dependency Closure (Generic)

## Purpose

Ensure that any newly introduced non-standard symbol in Android source code is fully wired before a task is considered complete.

This means:

- imports resolve
- the required dependency is declared
- the correct dependency scope is used
- the project's dependency-management style is respected
- the relevant build target compiles

## Core invariant

**Do not leave unresolved symbols introduced by your own changes.**

## What counts as non-standard

| Category | Examples | Required action |
|----------|----------|-----------------|
| AndroidX / Jetpack | `ExifInterface`, `ActivityResultLauncher`, `FileProvider` | import + dependency |
| UI libraries | `MaterialButton` | import + dependency |
| Test libraries | Espresso, JUnit, Intents | import + test dependency |
| Third-party libraries | ONNX, ML, networking, parsing libs | import + dependency |
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


## Dependency declaration rules

### If the project uses a version catalog

- add or reuse the version entry in the catalog
- add or reuse the library entry in the catalog
- reference the catalog alias from the relevant module build file
- do not pin a raw Maven coordinate in the module build file when catalog ownership is expected

### If the project does not use a version catalog

- add the dependency directly in the relevant Gradle build file
- keep the declaration minimal and local

## Scope rules

| Usage site | Preferred scope |
|------------|-----------------|
| Production app code | `implementation` |
| Local unit tests | `testImplementation` |
| Instrumentation / UI tests | `androidTestImplementation` |

## Pairing checklist

For every new symbol:

```text
- [ ] Identify the owning artifact
- [ ] Add the correct import
- [ ] Add or reuse the required dependency
- [ ] Use the correct scope
- [ ] Respect the project's dependency-management style
- [ ] Compile the relevant target
- [ ] Confirm no introduced symbol remains unresolved
```

## Build validation

Do not stop after editing source files.

Minimum validation:

- production code changes -> compile the relevant production target
- instrumentation test changes -> compile the relevant androidTest target
- mixed changes -> compile both relevant targets

## Version handling

- Do not silently invent random versions.
- Reuse project-consistent versions when possible.
- Reuse existing catalog entries when possible.
- Do not upgrade unrelated dependencies.

## Minimality rules

- No unrelated dependency upgrades
- No speculative dependency additions
- No build-system redesign unless explicitly requested
- No migration to a different dependency-management pattern unless explicitly requested

## Failure patterns to avoid

Do not:

- add an import without the matching dependency when one is required
- add a dependency without the matching import when source code uses the symbol
- use the wrong scope for test-only or production-only dependencies
- pin raw coordinates when the project expects version-catalog ownership
- stop after source edits while the build still contains unresolved symbols introduced by your changes

## Completion rule

A change that introduces new external symbols is complete only when:

- imports resolve
- dependency declarations are present and correct
- the correct scope is used
- the project's dependency-management style is respected
- the relevant build target compiles
- no unresolved introduced symbols remain
