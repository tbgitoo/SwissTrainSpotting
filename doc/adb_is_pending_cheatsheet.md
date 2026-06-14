# ADB MediaStore "is_pending" Cheat Sheet

## Problem
When pushing images via adb (e.g. `adb push file.jpg /sdcard/DCIM/`),
files may appear as **invisible / ghosted** in the Android photo picker.

This is due to MediaStore treating them as **`is_pending = 1`** (not yet published).

---

## Mental Model (IMPORTANT)

Think in SQL terms:

```
INSERT INTO media (..., is_pending=1);
-- no COMMIT
```

The fix is conceptually (but see details below):

```
UPDATE media SET is_pending = 0;
COMMIT;
```

---



## Fix (Using `_id`)

### Step 1 — Find IDs

```bash
adb shell content query --uri content://media/external/images/media --projection _id
```

```bash
adb shell content query --uri content://media/external/images/media --projection _display_name
```

Match rows by index.

---

### Step 2 — Update specific image

```bash
adb shell content update --uri content://media/external/images/media/21 --bind is_pending:i:0
```

✔ No fragile quoting  
✔ No filename ambiguity  
✔ Most stable targeted method  

---

## DO NOT DO (Common Pitfalls)

❌ Do NOT rely on filename filtering unless absolutely necessary
```bash
--where "display_name='file.jpg'"   # fragile quoting hell
```

❌ Leaving out the where and hoping it fixes all the pending image unfortunately fails

❌ Do NOT use commas in projection
```bash
--projection _id,_display_name     # breaks
```

❌ Do NOT rely on multiple projections
```bash
--projection _id --projection _display_name   # unreliable
```

❌ Avoid backslashes (line continuation)
```bash
\   # causes double shell parsing issues
```

❌ Do NOT update the collection URI with --where "_id=..."
✅ Update the specific row URI directly:
   content://media/external/images/media/<_id>

---

## Golden Rules

✔ Use **single-line commands**  
✔ Prefer `_id` over filenames  
✔ Avoid quoting complexity  
✔ If in doubt, use brute-force update  

---



## Why This Exists (Short Explanation)

- MediaStore expects apps to insert files via API
- adb push bypasses that lifecycle
- files remain "uncommitted"
- you manually finalize them

---

## TL;DR

> adb push = INSERT without COMMIT  
> is_pending=0 = COMMIT  

---

## Sanity Check

If your images do not appear in:
- Photo picker
- Gallery apps

→ Check is_pending and run this fix.

---

## Your Future Self Reminder 😄

The tool (`adb shell content`) is fragile, don't expect wonders.

Use the simplest working command and move on.
