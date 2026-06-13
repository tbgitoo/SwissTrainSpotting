You are deriving a LOCAL SKILL from an existing canonical one.

INPUT CONTEXT
- Canonical skill: ./external/android-testing/SKILL.md
- Local override target: ./.opencode/skills/android-testing/SKILL.md
- Project: SwissTrainSpottingApp (Android, Java + XML, Android Studio)
- Context: clarity, minimalism, correct planning

TASK
Create a LOCAL version of the SKILL.md by ADAPTING the canonical one.

PRIMARY GOAL
Preserve the canonical skill structure while making it locally useful for this Android project, WITHOUT making the skill brittle, overly specific, or setup-heavy.

STRICT RULES
- DO NOT rewrite from scratch
- DO NOT change structure or headings unless strictly necessary
- DO NOT expand scope
- DO NOT introduce new tools beyond those explicitly allowed
- DO NOT turn the skill into a setup or analysis workflow
- DO NOT include reporting, analysis, or “generate a summary” steps
- ONLY:
  - adapt paths
  - adapt naming
  - remove irrelevant sections
  - constrain scope
  - inject small project-specific defaults

IMPORTANT BEHAVIORAL CONSTRAINT
This skill must behave as an EXECUTION SKILL, NOT a SETUP SKILL.

That means:
- DO NOT include steps that analyze the project (e.g., scanning Gradle, reporting gaps)
- DO NOT include steps that repeatedly install/configure dependencies
- Assume a minimal standard Android test setup is already present or can be added only if strictly required
- Focus on writing and structuring tests, not preparing the environment

ABSTRACTION RULES (VERY IMPORTANT)
The resulting SKILL.md must clearly separate:

1. GENERAL TESTING CAPABILITIES
   - reusable guidance
   - activity-agnostic / screen-agnostic patterns
   - stable instructions valid as the app evolves

2. PROJECT DEFAULTS (MANDATORY SECTION)
   - small, explicitly marked section
   - contains current concrete examples
   - contains current app-specific assumptions

Do NOT hard-code current app structure into the core skill guidance.

That means:
- Prefer:
  - "activity under test"
  - "navigation between activities"
  - "key UI elements"
  - "user interaction"
  - "simple UI flow"
- Avoid core dependence on:
  - MainActivity
  - ClassificationActivity
  - fixed navigation chains
  - specific IDs or layouts

RULE:
Any concrete class names or flows MUST appear ONLY in the PROJECT DEFAULTS section.

ANDROID CONSTRAINTS
- Language: Java (NOT Kotlin)
- UI: XML layouts (NOT Compose)

TEST FRAMEWORKS (ALLOWED)
- JUnit (basic unit tests)
- Espresso (basic UI tests only)

NOT ALLOWED
- Compose testing
- Hilt / Dagger
- advanced mocking frameworks (unless already present in canonical skill)
- CI/CD or pipeline configuration
- screenshot testing unless explicitly requested

TESTING SCOPE (VERY IMPORTANT)
Keep it minimal and practical.

Include:
1. Basic unit tests for simple logic only

2. Espresso UI tests for general patterns such as:
   - launching an activity under test
   - verifying presence of key UI elements
   - performing simple user interactions
   - verifying navigation between activities via intents
   - validating a basic UI flow

Do NOT include:
- full test coverage strategies
- performance testing
- database testing unless already central to the canonical skill
- complex asynchronous handling
- configuration / device variation testing
- large or brittle test suites
- end-to-end frameworks beyond minimal Espresso usage

LOCAL ADAPTATION POLICY
Adapt the canonical skill so it becomes:
- shorter
- more practical
- focused on execution
- Android Studio / Java / XML aligned

Remove or compress any sections that:
- analyze project state
- propose multiple alternative architectures
- introduce optional advanced test categories

PROJECT DEFAULTS (MANDATORY)
Add a clearly labeled section at the end:

## Project Defaults (SwissTrainSpotting)

Include:
- Project name: SwissTrainSpotting
- Current primary activity: MainActivity
- Current secondary activity: ClassificationActivity
- Navigation style: button-triggered intents
- Representative flow:
  image selection → classification → result display

IMPORTANT:
- This section is illustrative only
- It must NOT define the core skill behavior
- The skill must remain valid as new activities or flows are added

PATH ADJUSTMENTS
Ensure correct Android Studio structure:
- Unit tests:
  app/src/test/java/...
- Espresso tests:
  app/src/androidTest/java/...

STYLE REQUIREMENTS
- Concise
- Structured
- Actionable
- Shorter than canonical version
- Prefer checklists over explanations
- No “generate reports” or narrative steps
- Preserve canonical structure as much as possible

QUALITY BAR
The final file must be:
- deterministic
- minimal
- execution-focused
- reusable
- project-aware but not project-fragile
- immediately usable by OpenCode agents

OUTPUT
Write the COMPLETE file to:
./.opencode/skills/android-testing/SKILL.md

FINAL STEP
Write the file directly.
Do not ask questions.
Do not explain.

NOTE ON REFERENCES
The reference folder is at `../external/android-skills/testing/testing-setup/references/`, indicate this at the end of the plan along with restrictive usage guidance.
