---
doc: checklist
status: approved
---

# Build Checklist

Build mode: learn

Commits: many small commits per slice — one per new feature, backend and Android separately (learner request). A slice's `Commit:` line names its last commit.

## Slices

- [x] **1. You can create an account, log out, and log back in**
  Becomes usable: The Android app opens on Splash → Welcome; Create Account (Name, Email, Password, City) saves a real user in Postgres with a hashed password and lands on the main screen (bottom nav + empty-closet state); Profile shows your details and Log out; Log In with email + password brings you back; reopening the app skips Welcome.
  Why now: Every later feature belongs to a user, so accounts come first — and this slice carries all the bootstrapping (Docker Compose with Postgres, Spring Boot project, Android project, Japandi theme, network config), proving the whole Android → backend → database path on day one.
  PRD ref: `prd.md > The Core Journey` (steps 1–4, 12–13), `prd.md > Account and Session`, `prd.md > Screens and Layout`, `prd.md > Look and Feel`
  Spec ref: `spec.md > Where It Runs and How Someone Tries It`, `spec.md > Components` (Auth and Users API, Errors and Health, Session Store, API Client, Splash Welcome and Auth Screens, Main Screen and Navigation), `spec.md > Data Model`, `spec.md > File Structure`, `spec.md > Look and Feel`
  Build: Add `docker-compose.yml` (db service), `.env.example`, `.gitignore` build/image entries; scaffold `backend/` (Spring Boot 3.x, Gradle) with `User`, `Session`, `AuthController`/`AuthService` (BCrypt), `AuthInterceptor`, `ApiExceptionHandler`, `HealthController`; scaffold `android/` with theme/colors/fonts, `SessionStore`, `ApiClient`, Splash, Welcome, Auth (create + log in modes), `MainActivity` with bottom nav (Calendar/Friends "coming soon"), `WardrobeFragment` with Clothes (empty state) and Outfits tabs, `ProfileFragment` with Log out, and the central + opening `AddMenuSheet` (items not wired yet).
  Verify (mechanical): `curl` the health endpoint; register → `201` with token; duplicate email → `409 email_taken`; wrong password → `401`; `GET /api/me` with token → `200`, without → `401`; logout then `/api/me` → `401`; `psql` shows `password_hash` starting with `$2` and no plain password; `./gradlew assembleDebug` succeeds for the Android app.
  Learner check: Start the backend, run the app on the emulator, create an account, see the welcome message and empty closet, open Profile and Log out, log back in with your email and password, then close and reopen the app — it should go straight to your wardrobe. Try a wrong password too.
  Commit: `Add real accounts: register, log in, log out, session`

- [x] **2. You can add a clothing photo and see it cleaned and auto-tagged in your closet**
  Becomes usable: + → Add clothes → pick a gallery photo → processing screen → "Background removed · 1/1 completed" → View item; the cutout appears in the Clothes grid with an AI-detected category and color.
  Why now: rembg and Gemini are the two unverified external services and the first half of the kernel ("your real wardrobe, digitized in seconds") — the riskiest part of the build, so it goes right after accounts. Verifies the Gemini model id, SDK calls and the rembg endpoint early.
  PRD ref: `prd.md > The Core Journey` (steps 5–7), `prd.md > Adding Clothes`, `prd.md > AI Item Details`, `prd.md > States and Boundaries` (Processing, Photo can't be processed)
  Spec ref: `spec.md > Stack` (Unverified; check at the start of the build), `spec.md > Components` (Items API, Image Storage, Background Removal Client, Tagging Service, Add Item Flow), `spec.md > External Services and Dependencies`, `spec.md > Important Failure Modes`
  Build: Add the rembg service and images volume to Compose; `RembgClient`, `ImageStorage` + `/images/**`, `GeminiConfig`, `TaggingService` (JSON schema output, failure → blank category), `Item` entity, `ItemController`/`ItemService` (upload + list, owner-checked); Android `AddItemActivity` (photo picker, processing/success/error + Try again), Clothes grid with Glide, empty state + Add clothes button wired.
  Verify (mechanical): `curl` rembg directly with a sample photo and confirm a transparent PNG; upload the same photo through `POST /api/users/{id}/items` with a token → `201` with a category and color; the PNG is served from `/images/...`; another user's token on that path → `403`; a non-image file → `400` and nothing saved; Android build succeeds.
  Learner check: Add 3–4 real clothing photos from the emulator gallery. Is the background cleanly removed? Did the AI get the category and color right? How does the processing screen feel?
  Commit: `Add clothing upload with background removal and AI tagging`

- [x] **3. You can open an item and correct or enrich its details**
  Becomes usable: Tapping an item opens its card: large image on white, name, Category, Color (swatch + name), Tags and Season; changes stick after reopening.
  Why now: The AI will mislabel some items, and the canvas and stylist rely on correct categories — so correction must exist before outfits are built from them.
  PRD ref: `prd.md > The Core Journey` (step 6), `prd.md > AI Item Details`, `prd.md > Screens and Layout` (Item card)
  Spec ref: `spec.md > Components` (Items API — PATCH, Item Detail)
  Build: a fixed list of item types (Blazer, Jeans, Sneakers…), each belonging to one of the four categories: `GET /api/item-types?q=` for autocomplete, the AI picks the type from that list at upload (learner request); `PATCH /api/items/{itemId}` with ownership check; `POST /api/items/{itemId}/rotate` (user-triggered rotate, learner request); Android `ItemDetailActivity` with a rotate option, name edit, type picker with suggestions (sets the category), color name/hex edit, tag chips, season selector; "We couldn't detect this — choose a category" when category is blank.
  Verify (mechanical): `curl` PATCH changes category/color/tags and a follow-up GET returns them; PATCH on another user's item → `403`/`404`; Android build succeeds.
  Learner check: Open an item, change its category or color, add a tag and season, go back and reopen — your changes should still be there.
  Commit: `Add editable item card`

- [ ] **4. You can generate a complete look with the AI stylist, with or without a hint**
  Becomes usable: + → Create outfit opens the canvas (Top with Outerwear overlapping, Bottom, Shoes on a white card); typing an optional hint and tapping Generate with AI fills every slot with items from your own closet, with the friendly toast when the hint has no match.
  Why now: This is the "aha" beat and the heart of the unique kernel — it comes as soon as there are items to style, not last. The backend validation (ownership, category, fallback) is the core learning goal: making an AI feature reliable.
  PRD ref: `prd.md > The Core Journey` (steps 8, 10), `prd.md > AI Stylist`, `prd.md > Outfit Canvas and Carousel` (layout, outerwear layer)
  Spec ref: `spec.md > Components` (Stylist Service, Outfits API — generate, Outfit Canvas), `spec.md > Important Failure Modes` (Gemini stylist fails), `spec.md > Decisions and Open Issues` (Learning focus)
  Build: `StylistService` (item list → Gemini JSON → validate ownership + category → random fill → message), `POST /api/users/{userId}/outfits/generate`, `StylistServiceTest` (foreign id, wrong-category id, empty category, total failure); Android `OutfitCanvasActivity` with one `ViewPager2` per slot over that category's items, hint field, Generate button with spinner, scroll-to-result and toast.
  Verify (mechanical): `./gradlew test` passes `StylistServiceTest`; `curl` generate with no hint and with a hint returns only the user's own item ids in the right categories; a hint with no match returns `hintMatched: false` plus the message and still a full look; Android build succeeds.
  Learner check: Open Create outfit, tap Generate with AI with no hint, then try a hint like a color you own and one you don't. Does the look make sense? Is it only your clothes?
  Commit: `Add AI stylist with validated outfit generation`

- [ ] **5. The canvas starts with a random outfit you can swipe piece by piece**
  Becomes usable: Opening Create outfit shows a random look right away; swiping any piece changes only that piece; missing categories show the dashed placeholder with + that opens Add clothes.
  Why now: Completes the manual half of the core loop around the canvas built in slice 4; cheap on-device logic, so it follows the riskier AI slice.
  PRD ref: `prd.md > The Core Journey` (steps 8–9), `prd.md > Outfit Canvas and Carousel`, `prd.md > States and Boundaries` (Partial wardrobe, Only one item in a category)
  Spec ref: `spec.md > Components` (Outfit Canvas), `spec.md > Look and Feel` (Canvas)
  Build: Random starting index per slot, per-slot swipe, `PlaceholderView` for empty categories wired to `AddItemActivity`, reload items when returning from an upload.
  Verify (mechanical): Android build succeeds; with a test account missing shoes, `GET` items confirms no SHOES and the canvas renders the placeholder (checked on the emulator via screenshot); reopening the canvas yields a different starting combination at least once in several tries.
  Learner check: Open Create outfit a few times — does it start differently? Swipe only the bottom — do the top and shoes stay? If you have no shoes yet, do you see "Add shoes to complete the look"?
  Commit: `Add random start and per-piece swipe on the canvas`

- [ ] **6. You can save an outfit and find it again after logging out and back in**
  Becomes usable: Save on the canvas stores the current look (manual or AI); it appears in the Outfits tab with its pieces and is still there after closing the app or logging out and back in.
  Why now: Closes the core loop ("success = step 11") and proves persistence end to end; everything it saves exists by now.
  PRD ref: `prd.md > The Core Journey` (steps 11–13), `prd.md > Saving Outfits`
  Spec ref: `spec.md > Components` (Outfits API — save and list), `spec.md > Data Model` (outfits)
  Build: `Outfit` entity, `POST`/`GET /api/users/{userId}/outfits` with ownership validation; Android Save button, `OutfitsFragment` list of saved looks with their piece thumbnails.
  Verify (mechanical): `curl` save with own ids → `201`; save with another user's item id → rejected; list returns newest first with nested items; after `docker compose restart backend`, the list is unchanged; Android build succeeds.
  Learner check: Save one swiped look and one AI look, check the Outfits tab, then log out, log back in, and confirm both are still there.
  Commit: `Add saving outfits and Outfits tab`

## Hands-on Checkpoints

- [x] Early usable behavior explored — after slice 2: the learner uploaded her own photos (blazer, trousers, sneakers, sweater) and reported everything works; her feedback during the slice added the one-photo limit, the single-item check, manual rotate and item types
- [ ] Final kick-the-tires exploration and feedback completed

## Final Review

- [ ] Final review complete — feedback resolved and learner confirms ready to ship

## Code Tour and App Map

- [ ] Learning activity complete — guided route, focused alternative, prior practice connected, or brief recap
- [ ] Optional edit and transfer reflection addressed — offered/declined/already covered/not applicable as appropriate
- [ ] `devpost/app-map.html` generated from finished code, checked, and shown, including a project-grounded practice to reuse

Activity and evidence: [what actually happened; real document/test/code references; unfinished work if interrupted]
Route and stops: [actual paths and symbols; guided stops completed, or reference-only route]
Edit outcome: [tried/kept/reverted/declined/not applicable; verification if changed]
Reflection: [offered/answered/declined/already covered — personal answer belongs only in the ignored profile]
Activity mode: [live app and editor, explicit static fallback, focused alternative, prior practice, or recap]

## Revisions

- Spec aligned to real email + password accounts (BCrypt, session token, Profile with Log out) — `spec.md` described a name-only mock login that contradicted the approved `scope.md` and `prd.md`; the learner confirmed real accounts at the start of `5-build`.
- Spring Boot 4.1.1 instead of 3.x — start.spring.io no longer offers 3.x (its open-source support has ended); the learner chose 4.1.1.
- City autocomplete on Create account (Open-Meteo Geocoding via `GET /api/cities`), city required and stored with country + coordinates — learner request during slice 1; small added cost (one public endpoint, no key).
- The learner's screen design (`Mixyn App Screens.pdf`) became the Look and Feel source: mocha primary color, Instrument Serif + Inter, reusable components and a single spacing scale; password minimum raised to 8 to match it; Apple/Google sign-in, Forgot password and Terms removed; Collections shown as "coming soon".
- Android `targetSdk` 36 instead of 37 — the build found that Android 17 silently drops connections from apps targeting 37 to the local network (the backend at 10.0.2.2) unless they request a new runtime permission.
- "Account created" is a separate screen instead of a dialog — learner request, from the updated design (page 4a).
- rembg runs from our own small image (`rembg/Dockerfile`) instead of `danielgatis/rembg` — that image has no arm64 build for Apple Silicon. The image pins the `u2net` model (baked in, so no first-run download) because rembg's newer default model is 1 GB, was killed for memory in a 4 GB Docker, and is non-commercial; requests must send `model=u2net`.
- rembg is published on host port 7010, not 7000 — macOS already uses 7000 (AirPlay Receiver).
- HEIC photos are supported on the server via `pillow-heif` in the rembg image — verified with a test HEIC before slice 2, so iPhone photos (and a future iOS app) work without client-side conversion.
- Gemini calls go through an ordered list of fallback models with an 8 s timeout each — the build found the free tier unreliable for a single model (503 "high demand", 12-27 s answers, and a 429 daily quota on `gemini-3.5-flash` after ~30 test calls). Lite models go first because their free quota is larger.
- Uploads are limited to one photo at a time with an explicit message, and photos that aren't a single clothing item (person, several items, not clothing) are rejected by the same Gemini call — learner requests during slice 2; no extra AI requests.
- Rotation is a user action on the item card (slice 3), not an AI task — the learner asked for AI auto-rotation, saw it miss an upside-down pair of shoes in testing, and chose a manual option instead.
- The stored cutout is cropped to the item and scaled to max 1024 px, and the phone shrinks photos to 1600 px before upload — keeps the grid tidy and uploads fast.
- Items get a **type** from a fixed list kept on the backend (about 30 types, each mapped to Top / Bottom / Shoes / Outerwear); the AI must choose from the list and the user picks with autocomplete — learner request after slice 2. A store's category API (Stradivarius, Bershka) was considered and dropped: no public API exists, so the list is our own. Built in slice 3 with the item card.
- The item card follows the updated design (pages 9–11): the single rotate button became an "Edit photo" sheet with size, rotate left/right, mirror, Reset and Done; the card also got the next-item arrow and the bottom bar — learner's updated design during slice 3. The backend's rotate endpoint became `POST /api/items/{id}/photo`.
- Items can have up to three colors from a fixed 18-color palette kept on the backend (the AI and the user both choose from it) instead of one free-text color — learner request; the AI had described a striped sweater as just "Grey".
- The next-item arrow beside the photo was removed from the item card — the learner tried it and saw no need for it.
- An item can have several seasons instead of one, and the "Edit photo" sheet got a light dim so its edge and rounded corners are visible — learner feedback while checking slice 3.
- On the canvas the hint is typed in an "Ask AI" sheet instead of a field under the canvas, and outerwear is changed with a small "Add a layer" button rather than by swiping — follows the learner's canvas design (page 14), where the top and the outer layer share the same area.
- The stylist understands occasions and full descriptions, and remembers the session's earlier requests so follow-ups change only what was asked — learner requests while checking slice 4. Memory lives on the phone and is sent with each request; the backend stays stateless.
- Weather came back into the PoC in a minimal form (a chip on the canvas and one line for the stylist, via Open-Meteo using the saved coordinates) — learner decision during slice 4; `scope.md` had it under Later / Explicitly Cut. The cold-weather layering step from the design stays out.
- Canvas layout follows the design more closely after the learner's review: outerwear is half a jacket over the left side of the top, shoes are smaller and lower, the header has the weather chip and Next, and the bottom panel has Add bag / Accessory ("Coming soon") beside Ask AI.
- The "Add a layer" button was removed from the canvas — it isn't in the learner's design (page 14). Outerwear now appears only when the stylist adds it; a manual way to choose a layer is an open question for the learner.
