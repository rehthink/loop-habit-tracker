# Phase 5: Food and water

## What was built
- `:core:network`: `GeminiFoodRecognizer` (Retrofit + kotlinx.serialization) calls
  `v1beta/models/{model}:generateContent` with structured JSON output (`response_schema`): items
  with name, quantity, unit (PIECE/KATORI/PLATE/GRAM/CUP/ML), confidence and a kcal guess. The
  model lives in `GeminiConfig.MODEL` (`gemini-3.8-flash`, free tier). The key is sent in the
  `x-goog-api-key` header and is never logged; there is no logging interceptor. HTTP 429 maps to
  RateLimited, IOException to Offline and 400/401/403 to "bad key". This is the app's only network
  call; INTERNET is declared only in this module.
- **Food table**: a separate read-only Room database shipped prepopulated (`assets/food/food.db`,
  183 common Indian dishes, breads, rice, dals, curries, snacks, sweets and drinks per standard
  portion). It's built by `tools/build_food_db.py` from `tools/food_data.csv`, using Room's exported
  schema. The script can also merge a filtered Open Food Facts export (with barcodes) for packaged
  foods.
- `FoodMatcher` (fuzzy matching on name and aliases, with transliteration folding such as
  daal→dal and poori→puri). Calories come from the table; Gemini's guess is used only for unmatched
  dishes and is marked as an estimate.
- `:feature:food`: CameraX capture → shrink to ~1024 px JPEG (app-private, not backed up) → review
  card. On the card, every dish's quantity (±), unit, removal, search-to-add and "add anyway" are
  editable, with a live total and a meal type from the time of day. Corrected portions are
  remembered per dish and pre-filled next time. With no internet, no key, a rate limit or a
  failure, the same card opens empty with search and recent meals, plus **Analyse later**. That
  queues the photo; a network-constrained worker analyses it and sends a "ready to review"
  notification, and the tick retries once a key exists.
- ML Kit barcode scanning (on-device). An unknown barcode opens a one-time "new packet" form that
  saves a custom food, filled in automatically next time.
- Water (± glasses) and tea/coffee counts; quick log water from the + button (with Undo), the
  widget and a notification action.
- `:feature:widgets`: a Jetpack Glance "Loop today" widget showing the four numbers, with +1
  water, Snap meal and Mood buttons. It refreshes every tick.
- Calories out (Mifflin-St Jeor + steps + workouts) shows on the Food screen once body details
  are set in Me › About you.

## How to test
Add a free key in Me › Food photo key. Snap a meal: on Wi-Fi the card should be pre-filled within a
few seconds. Turn on airplane mode and snap again: the empty card, search and Analyse later
should appear.

## Known limits
- The bundled values are typical home-style portions in the style of the IFCT 2017 tables, not an
  exact import. Packaged foods need the optional Open Food Facts merge, or one scan each.
- The "under 5 s" target depends on network speed and Gemini's free-tier latency.
