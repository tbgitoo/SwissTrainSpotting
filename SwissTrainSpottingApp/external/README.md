## Use of External Android Skills

This project includes the official Android Skills repository as a Git submodule:

https://github.com/android/skills

The full repository is retained for **provenance and reproducibility**, but only a minimal subset is actively used:

- `testing/testing-setup`

---

## Local Skill Specialization

The canonical testing skill (`testing-setup`) is **not used directly**.

Instead, a **locally constrained execution skill** is derived and maintained at:

```
.opencode/skills/android-testing/SKILL.md
```

This local skill is derived from:

```
external/android-skills/testing/testing-setup/SKILL.md
```

---

## Purpose of the Derivation

The canonical skill defines a **broad Android testing strategy**, including:

- Dependency injection setup
- Compose testing
- Screenshot testing
- End-to-end testing
- CI and coverage tools

For this project, only a **minimal, execution-focused subset** is required.
The derived local skill therefore enforces:

- Java + XML (no Compose)
- JUnit 4 + Espresso only
- Minimal UI and unit testing scope
- No DI frameworks, screenshot testing, or E2E frameworks

This ensures:
- clarity
- determinism
- suitability for a small, exam-driven project

---

## Reproducible Derivation Workflow

The local skill is generated and maintained via structured prompts:

- Initial derivation:
  ```
  external/prompts/CREATE_android-testing_skill.md
  ```

- Idempotent update (after upstream changes):
  ```
  external/prompts/UPDATE_android-testing_skill.md
  ```

The update process is **idempotent**:
- If the upstream skill has not changed → no modifications occur
- If upstream improves → relevant changes are selectively merged
- Local constraints are always preserved

---

## References Handling

The canonical skill includes additional reference material located at:

```
external/android-skills/testing/testing-setup/references/
```

The local skill explicitly points to this location but does not inline it.
This preserves:

- modularity of the upstream repository
- minimal size and focus of the local skill
- access to deeper technical documentation when needed
