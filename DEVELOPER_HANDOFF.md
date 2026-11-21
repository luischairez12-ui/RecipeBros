## Batch 4 — Screenshot recipe rescue
ScreenshotRecipeReader.kt performs on-device ML Kit text recognition on up to eight user-selected photos. RecipeEditor supports import, text preview, conservative Ingredients/Steps parsing and append-only editing. UI never claims the video itself was read. No new DB fields. Version bumped to 4; artifact name updated. Future work: supported local video file OCR frames and optional transcription/vision where permitted; do not treat opaque Facebook URLs as video bytes.

# Recipe Bros Batch 2 – compact UI handoff

Identity: Recipe Bros; clean white and sage green Modern Kitchen-inspired UI with warm terracotta Mama's recipe section representing Monterrey/Zacatecas; signature right-side pull-out tools tab. No Walmart branding, generic grocery list. Android package com.recipebros.app.

Core files:
- MainActivity.kt Android text/plain share intent integration.
- Models.kt immutable recipe and grocery models.
- RecipeStore.kt SQLite persistence, recipe CRUD and grocery actions.
- RecipeImporter.kt JSON-LD/microdata website extraction and fallback for inaccessible pages.
- Ui.kt Compose screen state and routing, recipe screens, side tab, Cook Mode, timer, beginner glossary, pantry matching.
- .github/workflows/build-apk.yml GitHub Actions debug build/upload artifact.

Debug priorities: compile with Gradle 8.9, Java 17, SDK 35; inspect Android Studio Build output; test Android share intents and blocked links; verify state restoration, back navigation, and accessibility; test local database preservation across version updates.

Rules for future work: preserve package/application ID, DB schema and migration behavior. Never label heuristic hints as AI analysis; never claim a video was understood when only link metadata exists. Do not invent Mama's real family recipes. Keep all recipes available offline after saving. Preserve signature right-side tab. No paid server dependencies unless opt-in.

## Batch 2 details / integration notes
- Ui.kt now provides a top-level `LocalTapFeedback` composition local, preference-backed click sound/haptics, an inset-aware `BoxWithConstraints` layout, compact home components, animated transitions, a draggable right-edge tab, contextual drawer actions, and screen-aware floating primary action.
- The drawer tab saves its vertical fractional position in SharedPreferences `recipe_bros_ui/dock_position`. Swiping left from the tab opens the drawer; tapping also opens it.
- UI preference booleans `click_sound` and `click_haptics` default to true. `View.playSoundEffect(CLICK)` is not an embedded audio file and respects system sound settings.
- Pantry text input and cooking step index are hoisted to the app level so the floating button / drawer shortcuts can update the current screen.
- Do not bring back a bottom navigation bar. Keep the Recipe Bros brand wordmark and family's Monterrey/Zacatecas section.
- APK app id remains `com.recipebros.app` and SQLite schema is unchanged. versionCode 2, versionName 1.1.0-batch2.
- QA: test status bar clearance, tiny phone/font scaling, orientation, movable tab on different screen heights, navigation/back, all FAB actions, haptics toggle & click sound, recipe import, data preservation across update, and cook-step transitions. Source still has not been compiled here.


## Batch 3 / 2026-11-21: Smart recipe-match fallback
- Added RecipeFinder.kt (TheMealDB V1 test key, dev-only; app-store releases require paid supporter key).
- RecipeImporter now checks publicly available explicit caption ingredients and automatically looks for similar meals when ingredients/steps are missing and a usable dish name is known.
- RecipeEditor adds manual "Find similar recipes" and candidates with attribution. Users must select "Fill missing details"; does not overwrite populated fields. Source reference recorded in notes. Original link always retained.
- Critical: Facebook video ID alone cannot reveal recipe if post/caption inaccessible. Never claim automatic video recognition. Never silently treat a similar recipe as the original recipe.
- No SQLite schema change, applicationId unchanged. Version 3.
