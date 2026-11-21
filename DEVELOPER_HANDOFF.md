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
