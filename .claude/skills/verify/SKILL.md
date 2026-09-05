---
name: verify
description: Build, install, and drive the GSearch app on an emulator to verify changes at the UI surface
---

# Verifying GSearch changes on an emulator

## Build & install

```bash
adb devices                      # expect an emulator/device listed
./gradlew :app:installDebug      # applicationId: com.msmobile.gsearch.debug
adb shell monkey -p com.msmobile.gsearch.debug -c android.intent.category.LAUNCHER 1
```

## Drive & capture

- Screenshot: `adb exec-out screencap -p > shot.png` (1080x2400 on the usual emulator).
- Foreground check: `adb shell dumpsys activity activities | grep mResumedActivity`
  (useful to prove an external intent fired, e.g. Chrome came to front).
- Type into a focused field: `adb shell "input text '...'"` — `%s` is space; brackets
  and parens are fine inside single quotes. `input keyevent MOVE_END` / `DEL` / `BACK`.

## Flows

- Launcher opens the visit list (seeded test data: Natanael, Janete, ...). The list may
  open with the search field focused and keyboard up — press BACK once before tapping.
- Tap a list card to open visit detail (householder, address, subject, date, type).
- Editing any detail field marks the visit "Draft"; save with the check (✓) button in
  the bottom pill bar (hidden while the keyboard is up — BACK first). Saving returns
  to the list, which shows each visit's raw subject text.

## Gotchas

- Restore seeded data after driving edits (undo button in the pill bar, or revert the
  edit) so later sessions see the expected fixtures.
