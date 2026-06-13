You are updating an EXISTING LOCAL SKILL using a canonical upstream skill.

INPUT CONTEXT
- Canonical skill: ./external/android-skills/testing/testing-setup/SKILL.md
- Local skill: ./.opencode/skills/android-testing/SKILL.md
- External references location:
  ../external/android-skills/testing/testing-setup/references/

PROJECT CONTEXT
- Android app (Java + XML, Android Studio)
- Minimal, execution-focused testing approach
- JUnit + Espresso only

TASK
Update the LOCAL SKILL.md by incorporating improvements from the canonical skill.

IMPORTANT: This is an UPDATE, not a rewrite.

---

## MERGE RULES (CRITICAL)

### 1. STRUCTURE
- Follow the canonical skill structure as baseline
- Preserve ordering of sections if possible
- Do NOT duplicate sections
- Do NOT create alternative versions of the same section

---

### 2. LOCAL CONSTRAINTS (MUST BE PRESERVED)

The following constraints MUST remain unchanged:

- Language: Java (NOT Kotlin)
- UI: XML (NOT Compose)
- Testing frameworks: JUnit + Espresso only
- Minimal scope (no advanced testing topics)
- Execution-focused (NOT setup-heavy)

If the canonical skill introduces conflicting content:
→ REMOVE or IGNORE those parts

---

### 3. EXECUTION FOCUS

The resulting skill must remain an EXECUTION SKILL:

- Remove any:
  - project analysis steps
  - reporting steps
  - multi-option architecture discussions
  - heavy setup workflows

- Keep:
  - actionable checklists
  - runnable examples
  - minimal explanations

---

### 4. EXAMPLES (VERY IMPORTANT)

- Keep examples GENERIC in the core sections:
  - use placeholders such as:
    * ActivityUnderTest.class
    * TargetActivity.class
  - do NOT hardcode MainActivity or specific app classes in core sections

- Ensure all examples are:
  - complete
  - runnable
  - safe (e.g., Intents.init() must use try/finally)

---

### 5. PROJECT DEFAULTS (PROTECTED SECTION)

Ensure the following section exists and is preserved:

## Project Defaults (SwissTrainSpotting)

- Keep all concrete app-specific details ONLY here
- Update only if strictly necessary
- Do NOT move these details into main sections

---

### 6. REFERENCES (PROTECTED SECTION)

Ensure the following section exists and is preserved:

## References

This section MUST:

- Point to:
  ../external/android-skills/testing/testing-setup/references/
- Explain that references are optional
- State that SKILL.md constraints take precedence

DO NOT:
- inline reference content
- remove this section
- duplicate reference material

---

### 7. SCOPE CONTROL

Ensure the final skill does NOT include:

- CI/CD or pipelines
- DI frameworks (Hilt/Dagger)
- Compose testing
- performance testing
- database testing (unless trivial and already present)
- device configuration testing
- large test suites

---

### 8. IDEMPOTENCY GUARANTEE

The final file MUST:

- not contain duplicated sections
- not contain conflicting instructions
- be stable under repeated application of this prompt
- be shorter or equal in size compared to canonical (after filtering)

---

## OUTPUT

- Overwrite:
  ./.opencode/skills/android-testing/SKILL.md

- Write the COMPLETE updated file

---

## FINAL STEP

Perform the merge and write the file.

Do NOT:
- ask questions
- explain changes
- produce intermediate output
