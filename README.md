# Recipe Bros 🍳 | Batch 2 — Compact UI

**Save • Cook • Learn • Share**

Native Android app built with Kotlin, Jetpack Compose, SQLite, and Jsoup. Design: premium white/sage-green UI with a signature **right-edge pull-out tool tab**. Dedicated warm **Las Recetas de Mamá ❤️** cookbook for recipes from Monterrey and Zacatecas.

## Batch 3 — Smart missing-ingredients lookup (prototype)

- First attempts the original recipe page, including schema.org Recipe, microdata, and explicitly labeled ingredient lines in accessible public captions.
- If ingredient lines or directions are absent, attempts a second-source search using a usable dish name found in the shared caption or page title.
- A new **Find similar recipes & missing ingredients** button lets you enter/edit a dish name, search for recipe matches, inspect their source and ingredient preview, then **Fill missing details** without overwriting any ingredients or instructions you already entered.
- Alternative-source recipe information is **never silently merged** into the saved recipe. When selected, the source and a warning are recorded in the recipe's notes; the original Facebook/TikTok/website URL is preserved.
- If a video link hides its title/caption, the app cannot know what food is in the video just from that opaque URL; enter the dish name to look up similar recipes. This is recipe lookup, **not video recognition or AI analysis**.
- Development search currently uses TheMealDB public V1 test key to look up a small library of meals (most dishes won't have a match). TheMealDB's terms require a supporter/production key for an app-store release. The API call goes directly from the Android phone to TheMealDB; no Recipe Bros server or user account is required.
- The match ranking is word overlap, not a guarantee the suggested recipe matches the shared video. Always check quantities, allergies, and food-safe cooking temperatures before using any suggestion.

## What's new in Batch 2 — Compact UI & Smart Dock

- Compact home screen: shorter branding header, one-line greeting, tighter search and quick tiles, and a 128dp family cookbook feature (down from a much taller panel).
- Recipe Bros logo-style fork badge and two-tone "Recipe Bros" wordmark at the top of all screens.
- Proper Android status/navigation bar insets to prevent text from overlapping phone system icons.
- **Removed the redundant bottom app navigation bar entirely.** Navigate with the right-side drawer.
- Draggable **right-edge pull-out handle**: drag vertically to reposition; tap or swipe left to open. Vertical position persists between launches.
- Sliding drawer with **screen-aware quick actions**, followed by global navigation shortcuts. For example: in Cook Mode, Next/Previous Step; on a recipe, Cook/Edit/Shop Ingredients; on Grocery, Add Item/Clear Checked.
- **Contextual floating + / action button**: Home/Recipes add a recipe; Mamá adds a family recipe; Grocery adds an item via dialog; Pantry adds ingredients; Recipe Detail adds ingredients to the list; Cook Mode advances (or finishes). Hidden on editor to avoid obstructing the save form.
- Springy press animations on the floating button and quick tiles; animated page swaps, drawer open/close, and cooking-step changes; expandable beginner tips animate height.
- Optional system click effects and gentle tap haptics, default on, configurable within the drawer. Sound effects and vibrations respect device capabilities and system settings.
- Original SQLite recipes/groceries and Android share-import flow are preserved, and the Android application ID is unchanged for in-place upgrades.

## Core features available from Batch 1

- Android system **Share → Recipe Bros** support for shared text and HTTP(S) links.
- Recipe website import: attempts schema.org `Recipe` JSON-LD and basic HTML microdata extraction (title, ingredients, directions, prep/cook time, portions).
- Graceful fallback for blocked, restricted, or video-only pages: save source link and manually add/edit the missing recipe details.
- Local editable recipe collection stored in SQLite, including favorites, source links, notes, and an optional `Las Recetas de Mamá` flag / region.
- Modern Kitchen-inspired white-and-sage dashboard, warm family-recipes section, and persistent signature right-edge quick-tools drawer.
- Step-by-step Cook Mode, an in-app countdown timer, and built-in **Beginner Cooking Assistant** cooking-term explanations.
- Store-agnostic grocery list: manually add items, import all ingredients from a recipe, check items, delete, and clear completed items.
- Search saved recipes and ingredient-based "What Can We Cook?" matching against **your existing saved cookbook**.
- No server, account, subscription, or API key required to use the app. Recipes and grocery items persist on the device and remain readable offline.

## Important limitations / honesty

- **This is not yet general AI video analysis.** Facebook, Instagram, TikTok, YouTube, and other sites may prevent third-party extraction. Sharing their link works; full ingredient/step extraction only works when a publicly accessible page provides supported structured data or microdata. Add missing details manually.
- The **Beginner Cooking Assistant** in Batch 1 is a local educational glossary, *not* an online AI model. A true AI explanation/accessible-video-transcription engine is a future opt-in batch and should not embed secret API keys inside an APK.
- Importers can fail on JavaScript-driven webpages, paywalls, user-login content, nonstandard formatting, dynamic short links, or restricted content.
- The grocery list imports ingredient lines as written; no automated pantry subtraction or product/price database.
- Recipe servings show the saved quantity; automatic numeric ingredient scaling is future work.
- Cooking timers run only while the screen is actively composing; no background alarm/notification in Batch 1. Don't rely on them for food safety.
- This source project **has not been compiled or device-tested** in this environment. Use GitHub Actions or Android Studio to produce and test an APK.

## Build APK using GitHub (easiest)

1. Create a **new empty GitHub repository**, e.g. `RecipeBros`. Do not mix these files into BoreLab/ZEUS/BuildDeck repos.
2. Extract this ZIP, open the extracted `RecipeBros_Batch2_CompactUI_Source` folder, and upload **all its contents** to the repository root, **including the hidden `.github` folder**.
3. Commit the files to your repo's `main` branch.
4. Go to **Actions → Build Recipe Bros APK** and choose the latest successful run (or **Run workflow**).
5. Under **Artifacts**, download `RecipeBros-Batch2-APK`. Unzip it; the install file is `app-debug.apk`.
6. Install the APK on Android. You may need to allow **Install unknown apps** for the file manager you used.
7. Test sharing a recipe from Chrome → Share → Recipe Bros, saving it, using Cook Mode, and sending ingredients to Grocery List.

## Build APK using Android Studio

- Use Java 17, Android Studio with Android SDK 35 and AGP 8.7.3-compatible Gradle 8.9.
- Open this folder, let Gradle sync, then choose **Build → Build APK(s)**.
- Debug artifact: `app/build/outputs/apk/debug/app-debug.apk`.

## Safety & data

- This app saves personal notes and recipe data in its private local SQLite database. Normal Android data-loss behavior applies on uninstall/clear storage. **Back up important family recipes separately until export/restore is added.**
- Recipe texts are not uploaded to a Recipe Bros cloud server; importing a public webpage connects directly from your device to that webpage's server.
- Recipe instructions are user/web-provided and may be incomplete or wrong. Always verify food-safe temperatures, times, allergies, and handling.

## Roadmap (not implemented yet)

- Batch 2: Import review UI improvements, manual photo/video note attachments, quantity scaling, ingredient categorization, robust app backup/export, better on-device cooking explanations, real alarms.
- Batch 3: Optional online AI analysis of supported recipes/video transcripts **only where access, permissions, and costs allow**, with clear confidence/unknown states.
- Later: Crew/bros shared shopping lists, a family-recipe sharing workflow, custom cooking photos, bilingual interface, pantry inventory, meal calendar, optional account sync.
