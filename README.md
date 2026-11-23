# Daily Puzzles for Light Phone III

A calm, 100% offline daily puzzle companion built with the official Light SDK (`com.setholson.dailypuzzles`).

## Features
- **Daily Word** — Classic 5-letter word deduction puzzle with a built-in OLED QWERTY keyboard, monochrome-first indicators, 365+ curated everyday solution words, and a 10,485-word offline dictionary.
- **Daily Fermi** — 3 back-of-the-envelope estimation questions per day from a 48-question bank, with an integrated scratchpad calculator, multiplicative error scoring, and step-by-step napkin math reveals.
- **Daily Chess** — Classic tactical checkmate puzzles (both White and Black to move) with full legal-move validation, explicit turn/miss indicators, progressive hints, and pawn promotion support.
- **Daily Crossword** — 45 bundled 9×9 medium crosswords with a built-in keyboard, full clue drawer, Check Word / Reveal Letter / Reveal Word helpers, and timer.
- **Intentional by Design** — Strictly today's puzzles only (prior days are automatically pruned; no archives, no infinite feeds, no streaks or push notifications). Includes an optional **Daily Unlock Window** and 1-tap **Lock Until Tomorrow** in Settings.
- **Zero Permissions, Zero Network** — Runs 100% offline with `permissions = []`. Puzzles are selected deterministically from the calendar date so every Light Phone user gets the exact same daily puzzles and puzzle numbers on the same day.
- **Spoiler-Free Share Cards** — LightOS-native result cards designed for clean full-color screenshot sharing via Album → Messages.

## Building & Testing
```bash
./gradlew -DlightSdk.toolOnly=true :tool:lightSdkValidateDependencies :tool:testDebugUnitTest :tool:lintRelease :tool:assembleRelease
```
