You are updating an EXISTING LOCAL SKILL by merging in changes from a canonical upstream skill.

INPUTS (BOTH REQUIRED)

1. Canonical skill:
   ./external/android-skills/testing/testing-setup/SKILL.md

2. Current LOCAL skill (PRIMARY BASELINE):
   ./.opencode/skills/android-testing/SKILL.md

---

TASK

Update the LOCAL SKILL by merging relevant improvements from the canonical skill.

IMPORTANT:
- The LOCAL skill is the PRIMARY document.
- The canonical skill is only a SOURCE OF POSSIBLE IMPROVEMENTS.

This is NOT a rewrite.

---

## CRITICAL MERGE LOGIC

### STEP 1 — Compare first
- Identify differences between canonical and local skill
- Determine whether canonical introduces improvements RELEVANT to this project

### STEP 2 — Apply selective updates only if needed

Apply a change ONLY IF:
- it improves clarity, correctness, or examples. The improvement needs to be substantial, otherwise prefer leaving the corresponding file content exactly as it is in the PRIMARY document.
AND
- it does NOT violate local constraints

If no such changes exist:
→ DO NOT modify the file

---

## HARD CONSTRAINTS (LOCAL SKILL MUST REMAIN)

The local skill MUST stay:

- Java (NOT Kotlin)
- XML (NOT Compose)
- JUnit + Espresso only
- minimal scope (NO CI, NO DI setup, NO screenshots, NO E2E frameworks)
- execution-focused (NO analysis steps, NO setup workflows)

If canonical contains:
- Compose testing → REMOVE
- DI frameworks → REMOVE
- screenshot testing → REMOVE
- test strategy analysis → REMOVE

---

## PROTECTED SECTIONS (DO NOT REMOVE OR REWRITE)

Ensure the following sections remain intact:

1. PROJECT DEFAULTS
2. REFERENCES (with external path)

You may only slightly adjust wording if strictly necessary.
Do NOT remove or relocate them.

---

## ANTI-DRIFT RULES

DO NOT:

- replace the entire document with canonical content
- reintroduce removed sections (analysis, DI setup, screenshot testing)
- expand scope beyond minimal execution
- duplicate sections
- rewrite unchanged sections

---

## IDEMPOTENCY RULE

If canonical introduces no relevant improvements:

→ RETURN THE LOCAL FILE UNCHANGED

(no edits, no restructuring, no formatting changes)

---

## OUTPUT

Overwrite:
./.opencode/skills/android-testing/SKILL.md

with the UPDATED version

---

## FINAL STEP

Perform the merge.

If nothing needs to change:
→ write the file EXACTLY as it currently exists.
