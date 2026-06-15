# Safe Java Edit

## Purpose
Ensure syntactically safe modifications to Java code by restricting changes to a precisely defined scope.

## Rules

### Scope restriction
- Modify ONLY the explicitly requested code region
- Do NOT modify:
  - package declaration
  - imports
  - class declaration
  - annotations
  - method signatures (unless explicitly requested)
  - braces outside the target region

### Allowed changes
- Method body logic
- Local variables
- Exception handling
- Logging

### Output format
- Return ONLY the modified code region
- No explanations
- No markdown fences
- No partial edits

### Structural safety checks (mandatory)
Before returning, verify:
- All braces are balanced
- Java syntax is valid
- Class declaration is unchanged
- Method signature is unchanged

### If the change requires structural modifications
- Do NOT attempt a partial edit
- Instead return a complete, valid Java file
``
