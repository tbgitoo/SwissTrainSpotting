# Java Full File Rewrite

## Purpose
Rewrite a complete Java source file safely when a change is too broad or structurally risky for partial patching.

Use this skill when the requested change affects multiple methods, imports, lifecycle methods, state restoration, or class-level structure.

Do not use this skill for small local edits that can safely be limited to a single method body.

## When to use
Use full-file rewrite mode when one or more of the following applies:
- imports may need to change
- multiple methods must change together
- lifecycle methods must be added or updated
- state persistence must be added
- structural consistency is more important than minimal diff size
- partial patching is likely to corrupt the file

Typical examples:
- adding `onSaveInstanceState`
- restoring state in `onCreate`
- adding EXIF-aware image loading
- updating resource handling across several decode paths
- fixing a Java file after a broken partial patch

## When NOT to use
Do not use this skill when:
- only a single method body needs small logic changes
- the task can be completed safely with a strictly scoped local edit
- the caller explicitly requests a method-only update

In those cases, use a local safe-edit skill instead.

## Core rule
Do not patch fragments of the class.

Instead, regenerate the complete final Java file as a syntactically valid, coherent source file.

## Requirements
- Preserve the existing package declaration unless explicitly told to change it
- Preserve the class name unless explicitly told to change it
- Preserve existing behavior unless the requested task requires a change
- Keep changes limited to the requested scope
- Prefer minimal, clear implementations
- Do not introduce speculative refactors
- Do not introduce new architecture layers unless explicitly requested
- Do not rename unrelated methods, fields, or IDs
- Do not remove existing functionality unless necessary to satisfy the request or fix a clear defect

## Structural safety rules
The returned file must:
- compile as valid Java
- contain balanced braces, parentheses, and brackets
- contain valid imports matching actual usage
- contain no truncated identifiers
- contain no duplicated methods caused by patching artifacts
- contain no orphaned code blocks
- contain no placeholder comments such as `TODO` unless explicitly requested
- contain no markdown fences or explanations

## Android-specific rules
For Android Java files:
- preserve the Activity or Fragment role unless explicitly requested otherwise
- preserve manifest-relevant class identity
- do not introduce new dependencies unless explicitly allowed
- do not migrate to new frameworks or architecture patterns unless explicitly requested
- keep lifecycle behavior minimal and compatible with the existing app structure

## Output contract
Return:
- the complete final contents of each modified Java file
- no explanations
- no commentary
- no diff format
- no partial snippets
- no ellipses
- no placeholders

If the file does not need to change, do not return it.

## Self-check before returning
Verify all of the following:
1. the file is complete, not partial
2. package declaration is valid
3. imports match usage
4. class declaration is valid
5. all methods are fully formed
6. all braces are balanced
7. there are no duplicated or cut-off sections
8. requested changes are present
9. unrelated behavior was preserved as much as possible
10. the output is plain Java source only

## Preferred workflow
1. Read the full current file
2. Determine whether the task is too broad for a local patch
3. If yes, rewrite the complete file
4. Apply only the requested changes
5. Re-check structural integrity
6. Return only the final file contents

## Decision rule
If there is any significant risk that partial editing could break class structure, use full-file rewrite mode.
