---
doc: spec
status: approved
---

# Mixyn — Technical Spec

Implements `prd.md` (approved). Scope: `scope.md > The POC Boundary`.

## How This Works, In Plain Language

Mixyn has three pieces that run on your laptop, plus one AI service in the cloud:

- **The Android app** (Java, XML layouts) is what you see and tap: the welcome screen, your wardrobe, the outfit canvas. It doesn't do any AI itself and holds no secrets. It remembers only *who you are* (your user id and a session token) so it can skip the welcome screen next time.
- **The Spring Boot backend** is the brain. The app sends it requests ("here's a photo," "give me an outfit"), and it does the work: cleans the photo, asks the AI what the item is, saves everything, and answers.
- **PostgreSQL** is the backend's spreadsheet: one table for users, one for clothing items, one for saved outfits. The item photos themselves are files in a folder; the database just remembers where each file is.
- **rembg** is a small open-source tool in its own Docker container. The backend hands it a photo and gets back the same photo with the background cut out.
- **Google Gemini** (cloud API) does two "understanding" jobs: it looks at a cleaned photo and says "this is a Top, color Burgundy", and it acts as the stylist: given a text list of your items and an optional hint, it picks one top, bottom, shoes and optional outerwear.

The key rule: **the backend never trusts the AI blindly.** When Gemini picks an outfit, the backend checks that every item id it returned really exists in *your* wardrobe and sits in the right category. That check is what turns the PRD's "the AI never places an item you don't own" from a hope into a guarantee.

Why this shape: it is the same stack as your long-term app (Android + Spring Boot + Postgres + Docker), which you chose on purpose so this project strengthens those skills. Everything else is kept as simple as possible: a single request per upload (no background jobs), images on disk instead of cloud storage, and real email + password accounts with a simple session token instead of a full Spring Security/JWT setup.

## The Core Journey Through the System
PRD ref: `prd.md > The Core Journey`.

1. **Open the app** → `SplashActivity` checks `SessionStore` (SharedPreferences) for a saved user id + token and confirms it with `GET /api/me`.
   - Found → go to `MainActivity` (wardrobe). *(Returning user, PRD step 12.)*
   - Not found → `WelcomeActivity`.
2. **Create Account** → `AuthActivity` sends `POST /api/auth/register {name, email, password, city}` → backend hashes the password with BCrypt, inserts a row into `users`, creates a session token → returns `{user: {id, name, email, city}, token}` → app saves id + token in `SessionStore`, shows the welcome message, then opens `MainActivity`.
   **Log In** → `POST /api/auth/login {email, password}` → same result on success; "Email or password is incorrect" otherwise.
   **Log out** → Profile → `POST /api/auth/logout` → token deleted, `SessionStore` cleared → `WelcomeActivity`. *(PRD step 13.)*
3. **Empty wardrobe** → `ClothesFragment` calls `GET /api/users/{id}/items` → empty list → shows the empty-closet illustration and **+ Add clothes**.
4. **Add clothes** → central **+** opens `AddMenuSheet` → Add clothes → Android photo picker → `AddItemActivity` shows the processing state and sends `POST /api/users/{id}/items` (multipart photo).
   Backend, in one request:
   a. `RembgClient` sends the photo to the rembg container → receives a transparent PNG.
   b. `ImageStorage` saves the PNG to `/data/images/{uuid}.png`.
   c. `TaggingService` sends the PNG to Gemini → receives `{category, colorName, colorHex}` JSON.
   d. `ItemService` saves an `items` row → returns the item JSON.
   The app then shows "Background removed · 1/1 completed" and **View item**.
5. **Item card** → `ItemDetailActivity` loads the image from `GET /images/{file}` and shows category and color. Edits are sent with `PATCH /api/items/{itemId}`.
6. **Create outfit** → `OutfitCanvasActivity` loads all items once, groups them by category, and **picks a random outfit on the phone** (no server call needed). Each slot is a horizontal `ViewPager2`: swiping changes only that slot.
7. **Generate with AI** → `POST /api/users/{id}/outfits/generate {hint}` → `StylistService` builds a text list of the user's items, asks Gemini for JSON ids, **validates** them, fills gaps, and returns `{top, bottom, shoes, outerwear, hintMatched, message}` → the app scrolls each slot to the returned item and shows a toast if `hintMatched` is false.
8. **Save** → `POST /api/users/{id}/outfits {topId, bottomId, shoesId, outerwearId, source, hint}` → row in `outfits` → `OutfitsFragment` lists them via `GET /api/users/{id}/outfits`.

```
 Android app ──HTTP (10.0.2.2:8080)──▶ Spring Boot ──▶ PostgreSQL (users, items, outfits)
                                          │  ├──▶ /data/images (PNG files, Docker volume)
                                          │  ├──▶ rembg container (background removal)
                                          │  └──▶ Gemini API (tagging + stylist, HTTPS, API key)
```

## Stack

| Layer | Choice | Why (learner decision unless marked) | Docs |
|---|---|---|---|
| Android app | **Java + XML layouts**, Android Studio, minSdk 26, compileSdk 37, **targetSdk 36** (Android 17 blocks apps targeting 37 from reaching the laptop backend without a runtime permission) | Same as the learner's current project. | https://developer.android.com/guide |
| Android libs | Retrofit 2 + Gson (HTTP), Glide (images), Material Components (BottomNavigationView, BottomSheet, TabLayout), ViewPager2 (per-slot swipe), Photo Picker (`ActivityResultContracts.PickVisualMedia`, no storage permission) | *Derived:* standard, well-documented Java-friendly choices. | https://square.github.io/retrofit/ · https://bumptech.github.io/glide/ · https://developer.android.com/training/data-storage/shared/photopicker |
| Backend | **Spring Boot 4.1.1**, Java 21, Gradle | Learner's long-term stack. *Derived:* Gradle to match Android; Maven is fine if the learner prefers. | https://docs.spring.io/spring-boot/ |
| Backend libs | Spring Web, Spring Data JPA, PostgreSQL driver, Validation | *Derived:* the minimal Spring set for REST + DB. | https://spring.io/guides/gs/accessing-data-jpa |
| Database | **PostgreSQL 16** in Docker | Learner's choice, same as the long-term app. | https://www.postgresql.org/docs/16/ |
| Background removal | **rembg** (`danielgatis/rembg` image) in Docker | Learner's choice: free, local, no key, no limits. | https://github.com/danielgatis/rembg |
| Tagging + stylist | **Google Gemini API**, a current **Flash** model, via the **Google Gen AI Java SDK** (`com.google.genai:google-genai`) | Learner's choice (asked for Gemini 1.5 Flash; see note). | https://ai.google.dev/gemini-api/docs · https://github.com/googleapis/java-genai |
| Orchestration | **Docker Compose** | Learner's choice. | https://docs.docker.com/compose/ |

**Unverified; check at the start of the build:**
- **Gemini model name.** The learner asked for Gemini 1.5 Flash. As far as the agent knows, the 1.5 family was retired in 2025. Use the current Flash model listed at https://ai.google.dev/gemini-api/docs/models and put its id in `GEMINI_MODEL` so it's a one-line change.
- **Gemini free-tier rate limits** (https://ai.google.dev/gemini-api/docs/rate-limits). Each upload makes 1 call and each Generate makes 1 call.
- **Google Gen AI Java SDK**: current version, how the client picks up the API key, and the exact method names for image input and JSON-schema output.
- **rembg HTTP server**: the exact endpoint and form-field name (believed to be `POST /api/remove`, multipart field `file`), plus the server start command.
- ~~Spring Boot 3.x~~ → verified: start.spring.io offers only 4.x; **4.1.1** chosen by the learner.

## Where It Runs and How Someone Tries It

Everything runs locally. No deployment (optional for the hackathon, not chosen).

**Requirements:** Docker Desktop, JDK 21, Android Studio with an emulator (API 26+), and a Gemini API key from https://aistudio.google.com/apikey.

**Setup:**
1. `cp .env.example .env` and put your key in `GEMINI_API_KEY=` (the `.env` file is git-ignored; never commit it).
2. `docker compose up --build` starts `db` (Postgres, port 5432), `rembg` (port 7000) and `backend` (port 8080).
   *During development:* `docker compose up db rembg` plus `./gradlew bootRun` in `backend/` gives faster restarts.
3. Check that it's up: `curl http://localhost:8080/api/health` → `{"status":"ok"}`.
4. Open `android/` in Android Studio, start an emulator, and press Run. The app calls `http://10.0.2.2:8080`, which is the emulator's name for your laptop.

**First-run note:** rembg downloads its model (~170 MB) on the first background removal, so the first upload can take noticeably longer. Do one warm-up upload before recording.

**Demo recording:** use the emulator's screen recorder (or macOS screen recording). Follow `scope.md > What "Working" Looks Like`: create an account, upload a few items (have 2+ tops, 2+ bottoms, 1–2 shoes and an outerwear photo ready in the emulator gallery, e.g. by dragging files onto the emulator), swipe on the canvas, Generate with and without a hint, then Save.

**Submission:** a public GitHub repo (without `.env`, `learner-profile.md` or uploaded images) plus the demo video. Handled in `6-ship`.

## Look and Feel
Carries forward `prd.md > Look and Feel`. **Source of truth: the learner's screen design (`Mixyn App Screens.pdf`, kept outside the repo)**, supplied at the start of the build; it replaces the original sketch below where they differ.

As built:
- **Colors** (`res/values/colors.xml`): `mocha #7B6453` (primary buttons, splash, avatar), `ink #1F1B17` (text, central +, dark buttons), `ink_muted #8A837B`, `sand_bg #F4F1ED`, `surface #FBFAF7`, `line #E3DDD5`, `error #4F0005` (Pantone 4975C "Red Inferno": wrong-input border + message, and Log out).
- **Type:** *Instrument Serif* for the logo and titles, *Inter* (sans-serif) for everything else; both OFL, bundled in `res/font/`.
- **Rules (learner request):** all texts in `strings.xml`, all colors in `colors.xml`, one spacing scale in `dimens.xml` (4/8/12/16/24/32/48 dp), styles in `styles.xml`. Reusable components: `MixynBottomBar`, `MixynLogoHeader` ("Mixyn ☰", "Profile"), `MixynBackHeader` (← + title + subtitle), `MixynTextField` (label + field + show/hide password + error), and one button base with color variants (Primary / Outline / Dark / Soft).
- **Removed from the design for the PoC:** Continue with Apple/Google, Forgot password, Terms checkbox. **Collections** tab is visible but "coming soon".

Original sketch (superseded where the design differs):

- **Colors** (`res/values/colors.xml`):
  `sand_bg #F3EEE6` (window background) · `card_white #FFFFFF` (cards, canvas) · `ink #2B2926` (text, primary buttons, central +) · `ink_muted #7A736B` (secondary text, disabled tabs) · `line #E3DBD0` (dividers, placeholder outlines).
  No brand accent: color comes from the clothes.
- **Type:** a serif for the logo and screen titles (e.g. *Cormorant Garamond* or *Playfair Display*) and a sans-serif for everything else (e.g. *Inter*, or the system default if time is short), bundled in `res/font/`.
- **Style:** generous whitespace, 16dp side margins, rounded white cards (12dp radius, 1dp `line` stroke, no heavy shadows), black pill-shaped primary buttons, a large black circular central **+**.
- **Empty closet:** one simple vector drawable (`empty_closet.xml`) of an open wardrobe with three hangers and two drawers, in `line`/`ink_muted` strokes.
- **Canvas:** a white card; slot images use `fitCenter` on white so the cutouts stand out; outerwear is drawn overlapping the top slot.
- **Disabled "coming soon" tabs:** icon + label in `ink_muted`; tapping shows a small "Coming soon" toast.
- **Copy tone:** warm and short. Use the PRD strings as written ("Your closet is empty", "Add your first piece…", "We couldn't find anything burgundy. Here is another stylish option!").

## Components

### Backend

#### Auth and Users API
`AuthController` + `AuthService` + `User` and `Session` entities. Real email + password accounts.
- `POST /api/auth/register` `{name, email, password, city, country, latitude, longitude}` → `201 {user, token}`. Email is trimmed, lower-cased and must be unique → `409 {"error":"email_taken"}` (the app suggests Log In). Password min 8 chars → `400`. The city must come from the autocomplete (coordinates required). The password is stored only as a **BCrypt hash** (`spring-security-crypto`'s `BCryptPasswordEncoder`; no full Spring Security).
- `POST /api/auth/login` `{email, password}` → `200 {user, token}` or `401 {"error":"invalid_credentials"}` (same response for unknown email and wrong password).
- `POST /api/auth/logout` → deletes the current token → `204`.
- `GET /api/me` → `200 {id, name, email, city, country}` or `401`. Used on splash to confirm the stored session still works.
- `GET /api/cities?q=Lvi` (public) → up to 8 `{name, region, country, latitude, longitude}`. `CityService` proxies the free **Open-Meteo Geocoding API** (no key), keeps only populated places, removes duplicates, and searches in Ukrainian when the query is Cyrillic. Failure → `502 city_search_failed`.
- **Token:** a random 32-byte value (Base64URL) stored in `sessions`; the app sends it as `Authorization: Bearer <token>`.
- **Ownership:** `AuthInterceptor` resolves the token to a user on every `/api/**` call except register/login/health (`401` if missing/unknown). For `/api/users/{userId}/**` the path id must equal the token's user, and item/outfit ids are checked against that user → otherwise `403`/`404`. This is what makes "each user sees only their own clothes" a guarantee, not a UI convention.
PRD ref: `prd.md > Account and Session`.

#### Items API
`ItemController` + `ItemService`.
- `POST /api/users/{userId}/items` (multipart field `photo`, JPEG/PNG/HEIC up to 10 MB) → `201 Item`. Steps: rembg → store PNG → tag → save. See `prd.md > Adding Clothes`, `prd.md > AI Item Details`.
- `GET /api/users/{userId}/items` → `200 [Item]`, newest first.
- `PATCH /api/items/{itemId}` `{category?, colorName?, colorHex?, name?, tags?, season?}` → `200 Item`. This is how the user corrects AI results.
- `Item` JSON: `{id, imageUrl, category, colorName, colorHex, name, tags[], season, createdAt}`, where `imageUrl` = `/images/{file}`.

#### Image Storage
`ImageStorage` writes PNGs to `${IMAGE_DIR}` (`/data/images`, a Docker volume) as `{uuid}.png`. `WebConfig` serves them at `GET /images/**`.

#### Background Removal Client
`RembgClient` uses Spring `RestClient` to send the photo to `${REMBG_URL}` and returns the PNG bytes. Timeout 60 s (to cover the first-run model download).
PRD ref: `prd.md > Adding Clothes`.

#### Tagging Service
`TaggingService` makes one Gemini call with the PNG plus a short prompt. JSON output is constrained to:
```json
{ "category": "TOP|BOTTOM|SHOES|OUTERWEAR", "colorName": "Khaki", "colorHex": "#8F8A5C" }
```
If the call fails or returns something invalid, the item is **still saved** with `category = null` and the item card shows "We couldn't detect this — choose a category". Items without a category don't appear on the canvas until one is set.
PRD ref: `prd.md > AI Item Details`.

#### Stylist Service
`StylistService.generate(userId, hint)`:
1. Load the user's categorized items and build a compact text list: `id | category | colorName | name | tags`.
2. Make one Gemini call with that list plus the hint (or "no hint"). JSON output:
   ```json
   { "topId": 12, "bottomId": 7, "shoesId": 3, "outerwearId": null, "hintMatched": true }
   ```
3. **Validate:** each id must belong to this user *and* be in the right category. Any invalid or missing slot is filled with a random item from that category; if the category is empty, the slot is `null` and the app shows the placeholder.
4. If `hintMatched` is false and a hint was given, `message` = "We couldn't find anything {hint}. Here is another stylish option!"
5. Return `{top, bottom, shoes, outerwear, hintMatched, message}` with full `Item` objects.
Outerwear is optional: the prompt tells the stylist it *may* add one (resolves `prd.md > Open Questions` using the PRD's assumption).
PRD ref: `prd.md > AI Stylist`.

#### Outfits API
`OutfitController`.
- `POST /api/users/{userId}/outfits/generate` `{hint?}` → stylist result (above).
- `POST /api/users/{userId}/outfits` `{topId, bottomId, shoesId, outerwearId, source: "MANUAL"|"AI", hint}` → `201 Outfit`. Ids are validated for ownership; slots may be null.
- `GET /api/users/{userId}/outfits` → `200 [Outfit]` with nested items, newest first.
PRD ref: `prd.md > Saving Outfits`.

#### Errors and Health
`ApiExceptionHandler` maps errors to `{"error": code, "message": text}`: `401 unauthorized`/`invalid_credentials`, `403 forbidden`, `404 not_found`, `409 email_taken`, `400 invalid_image`, `502 background_removal_failed`. `GET /api/health` → `{"status":"ok"}`.

### Android App

#### Session Store
`SessionStore` wraps SharedPreferences and holds `userId`, `userName` and the session `token` (never the password). `ApiClient` adds the token as a Bearer header; a `401` clears the session and returns to Welcome.
PRD ref: `prd.md > Account and Session`.

#### API Client
`ApiClient` (Retrofit, base URL `http://10.0.2.2:8080/`, 90 s read timeout for uploads) + a `MixynApi` interface + DTOs. Cleartext HTTP to `10.0.2.2` is allowed only through `res/xml/network_security_config.xml`.

#### Splash, Welcome and Auth Screens
`SplashActivity` (logo, ~1 s, then routes on the session) → `WelcomeActivity` (logo, "Mixyn", description, Create Account / Log In) → `AuthActivity` (one layout in two modes: create = name + email + password + city; log in = email + password; friendly inline errors for wrong credentials and an already-used email, with a "Log In instead" link). After creating, `AccountCreatedActivity` shows a full "Account created" screen (design page 4a: arch art, "Welcome to Mixyn, {name}.", Snap / Mix / Relax, **Let's go** and **I'll look around first**) and then opens Main; from slice 2, **Let's go** opens Add clothes.
PRD ref: `prd.md > Account and Session`, `prd.md > Screens and Layout`.

#### Main Screen and Navigation
`MainActivity` has a `BottomNavigationView` with Home, Calendar, a central **+**, Friends, and Profile. Home hosts `WardrobeFragment` with a `TabLayout`: **Clothes** (`ClothesFragment`, a grid or the empty state) and **Outfits** (`OutfitsFragment`). Calendar and Friends are disabled and show a "Coming soon" toast. **Profile** opens `ProfileFragment`: name, email, city and **Log out**; anything else there is "coming soon". **+** opens `AddMenuSheet` (a BottomSheet) with Add clothes, Create outfit, and OutfitCHECK (disabled, "coming soon").
PRD ref: `prd.md > Adding Clothes`, `prd.md > Screens and Layout`.

#### Add Item Flow
`AddItemActivity` launches the Photo Picker, then shows the processing state (progress indicator + "Removing background…"), then "Background removed · 1/1 completed" + **View item**, or an error + **Try again**.
PRD ref: `prd.md > Adding Clothes`, `prd.md > States and Boundaries`.

#### Item Detail
`ItemDetailActivity` shows the image, the name field (+ Edit), Category (a picker with the 4 values), Color (swatch + name; tap to edit the name, with the hex optional), Tags (chips: Casual, Night out, Streetwear, Date night, Work, Favorites, Sports, Cozy), and Season (All, Spring, Summer, Autumn, Winter). Each change sends a `PATCH`.
PRD ref: `prd.md > AI Item Details`.

#### Outfit Canvas
`OutfitCanvasActivity`: a white card stacking a top slot (with an outerwear `ViewPager2` overlapping it), a bottom slot, and a shoes slot, each a horizontal `ViewPager2` over that category's items. It opens on a random index per slot. An empty category shows a placeholder view (dashed `line` outline, **+**, "Add {category} to complete the look") that opens the Add Item Flow. Below the card: the hint `EditText`, **Generate with AI** (a spinner while waiting), and **Save**.
PRD ref: `prd.md > Outfit Canvas and Carousel`, `prd.md > AI Stylist`, `prd.md > Saving Outfits`.

## Data Model

PostgreSQL, created by JPA (`spring.jpa.hibernate.ddl-auto=update`, a PoC simplification).

**users**: `id bigserial PK`, `name varchar(50)`, `email varchar(254) unique` (stored lower-case), `password_hash varchar(72)` (BCrypt), `city varchar(80)`, `country varchar(80)`, `latitude`, `longitude` (for weather later), `created_at timestamptz`.

**sessions**: `token varchar(64) PK`, `user_id → users.id`, `created_at timestamptz`. (No expiry in the PoC.)

**items**: `id bigserial PK`, `user_id → users.id`, `image_file varchar` (e.g. `3f2a….png`), `category varchar null` (TOP, BOTTOM, SHOES, OUTERWEAR), `color_name varchar null`, `color_hex varchar(7) null`, `name varchar null`, `tags varchar null` (comma-separated), `season varchar null`, `created_at timestamptz`.

**outfits**: `id bigserial PK`, `user_id → users.id`, `top_item_id`, `bottom_item_id`, `shoes_item_id`, `outerwear_item_id` (all `→ items.id`, nullable), `source varchar` (MANUAL/AI), `hint varchar null`, `created_at timestamptz`.

| Data | Lives in | Updated by | After closing and reopening |
|---|---|---|---|
| Who you are | SharedPreferences (`userId`, `userName`, `token`) | Create Account / Log In; cleared by Log out | Still there, so Welcome is skipped |
| Your account (name, email, password hash, city) | Postgres `users` | `POST /api/auth/register` | Persists (Docker volume `pgdata`) |
| Active sessions | Postgres `sessions` | Log In / Register / Log out | Persist until Log out |
| Item photos | `/data/images` (Docker volume `images`) | Upload | Persist |
| Item details | Postgres `items` | Upload (AI), `PATCH` (user) | Persist |
| Current canvas selection | Memory in `OutfitCanvasActivity` | Swipes, Generate | Gone; a fresh random outfit next time (by design) |
| Saved outfits | Postgres `outfits` | Save | Persist |
| Hint text | Memory only (stored with a saved AI outfit) | Typing | Gone |

## File Structure

```
Mixyn/
├── docker-compose.yml          # db (postgres:16), rembg, backend; volumes pgdata + images
├── .env.example                # GEMINI_API_KEY=, GEMINI_MODEL=, DB creds (no secrets)
├── .gitignore                  # already ignores .env, learner-profile.md; add build dirs, images
├── README.md                   # what Mixyn is, setup + run steps, demo link
├── backend/
│   ├── Dockerfile              # multi-stage Gradle build → JRE 21 image
│   ├── build.gradle / settings.gradle
│   └── src/
│       ├── main/java/com/mixyn/
│       │   ├── MixynApplication.java
│       │   ├── config/         # WebConfig (serves /images/**), GeminiConfig (client bean), CORS off
│       │   ├── common/         # ApiExceptionHandler, HealthController
│       │   ├── user/           # User, Session, repositories, AuthController, AuthService, AuthInterceptor, UserDtos
│       │   ├── item/           # Item, Category, ItemRepository, ItemService, ItemController, ItemDtos
│       │   ├── outfit/         # Outfit, OutfitRepository, OutfitController, StylistService, OutfitDtos
│       │   ├── ai/             # RembgClient, TaggingService, GeminiJson helpers, prompts
│       │   └── storage/        # ImageStorage
│       ├── main/resources/application.yml   # reads env: DB_*, REMBG_URL, GEMINI_*, IMAGE_DIR
│       └── test/java/com/mixyn/
│           └── outfit/StylistServiceTest.java  # validation: foreign ids, wrong category, empty slots
├── android/
│   ├── build.gradle / settings.gradle
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mixyn/app/
│       │   ├── api/            # ApiClient, MixynApi, dto/ (User, Item, Outfit, GenerateResult…)
│       │   ├── session/        # SessionStore
│       │   └── ui/
│       │       ├── splash/     # SplashActivity
│       │       ├── welcome/    # WelcomeActivity
│       │       ├── auth/       # AuthActivity
│       │       ├── main/       # MainActivity, WardrobeFragment, ClothesFragment, OutfitsFragment, ProfileFragment, AddMenuSheet, adapters
│       │       ├── additem/    # AddItemActivity
│       │       ├── item/       # ItemDetailActivity
│       │       └── canvas/     # OutfitCanvasActivity, SlotPagerAdapter, PlaceholderView
│       └── res/
│           ├── layout/         # one XML per screen/fragment/row
│           ├── values/         # colors.xml, themes.xml, strings.xml, dimens.xml
│           ├── font/           # serif + sans font files
│           ├── drawable/       # empty_closet.xml, ic_* icons, bg shapes
│           ├── menu/           # bottom_nav.xml
│           └── xml/            # network_security_config.xml
└── devpost/                    # planning docs (learner-profile.md is git-ignored)
```

## External Services and Dependencies

### Google Gemini API
- **Auth:** API key from Google AI Studio, in `.env` as `GEMINI_API_KEY`, read only by the backend.
- **Model:** `GEMINI_MODEL` env var, set to a current Flash model (verify at https://ai.google.dev/gemini-api/docs/models).
- **Calls** (via the Google Gen AI Java SDK `generateContent`, with JSON output via a response MIME type of `application/json` plus a response schema; exact Java method names to be verified against https://github.com/googleapis/java-genai):
  - *Tagging:* input = PNG bytes (inline image part) + text prompt; output = `{category, colorName, colorHex}`.
  - *Stylist:* input = text only (item list + hint); output = `{topId, bottomId, shoesId, outerwearId, hintMatched}`.
- **Cost / limits:** a free tier with per-minute and per-day limits (verify at https://ai.google.dev/gemini-api/docs/rate-limits). A demo session makes roughly 10–20 calls.
- **Docs:** https://ai.google.dev/gemini-api/docs/structured-output · https://ai.google.dev/gemini-api/docs/image-understanding

### rembg
- **Container:** `danielgatis/rembg`, run in server mode on port 7000 (start command to verify in the README; believed to be `s --host 0.0.0.0 --port 7000`).
- **Call:** believed to be `POST http://rembg:7000/api/remove` with multipart field `file` → `image/png` bytes. Verify early with a `curl` and one sample photo.
- **Cost:** free, local, no key. The first call downloads the u2net model.
- **Docs:** https://github.com/danielgatis/rembg#usage-as-a-server

### Open-Meteo Geocoding (city autocomplete)
- `GET https://geocoding-api.open-meteo.com/v1/search?name=…&count=15&language=en|uk`, called only by the backend. Free, no key. The same provider can supply weather later.
- Results need ≥ 3 characters; very short queries return nothing.

### PostgreSQL
- The `postgres:16` image, with credentials from `.env` (`POSTGRES_DB=mixyn`, `POSTGRES_USER`, `POSTGRES_PASSWORD`; local-only values).

## Important Failure Modes

- **rembg fails, or the photo is unreadable** → backend `502`/`400`, nothing saved → the processing screen shows "We couldn't process this photo" + **Try again** (`prd.md > States and Boundaries`).
- **Gemini tagging fails** (network, rate limit, bad JSON) → the item is saved with a blank category/color → the card asks the user to choose. The upload never fails because of tagging.
- **Gemini stylist fails or returns invalid ids** → the backend fills the affected slots with random valid items; if the whole call failed, `message` = "The stylist is taking a break — here's a random look instead." The canvas is never left blank. *(Assumption: the PRD didn't specify the total-failure case; this follows its "never a blank canvas" rule.)*
- **Backend not running or unreachable from the emulator** → the Android app shows "Can't reach Mixyn — is the server running?" instead of crashing. (Common cause: using `localhost` instead of `10.0.2.2`, or the missing network security config.)
- **Slow first upload** (rembg model download) → the processing state covers it; the 90 s client timeout leaves room. Do a warm-up upload before recording.

## What Was Simplified and Why

- **Real accounts, simple sessions:** email + BCrypt-hashed password (learner decision in `prd.md > Product Decisions`), with an opaque random token in a `sessions` table checked by one interceptor, instead of full Spring Security, JWT, token expiry, password reset or email verification.
- **Images on a local disk volume** instead of cloud object storage. The same "path in the DB, file elsewhere" pattern moves to S3 or GCS later.
- **One synchronous upload request** instead of a job queue. The fuller version would process asynchronously and let the app poll or receive a push.
- **Random outfit picked on the phone**, not on the server: there's no AI involved, so there's no reason to add a round trip.
- **JPA auto-creates tables** (`ddl-auto=update`) instead of migrations. Flyway would be the next step for the real app.
- **Stylist sees text, not photos**: cheaper and faster, and category + color + name + tags are enough for a hint like "burgundy". Sending images to the stylist is a later enhancement.
- **Basic Android layouts** with the Japandi identity applied through the theme (learner decision: time goes to the backend).
- **Everything local, no deployment**: the hackathon needs a video and a repo, not a live URL.

## Decisions and Open Issues

**Learner decisions:**
- **Native Android (Java + XML) + Spring Boot + PostgreSQL + Docker**, "same as my long-term app" over the fastest path, because the goal is strengthening this exact stack. Accepted tradeoff: Gradle, emulator and Spring setup eat into the 2–4 hours, so the Android UI stays basic.
- **rembg in Docker** for background removal: free, local, no limits. Accepted tradeoff: a large first download and slower processing on a laptop.
- **Google Gemini** for tagging and the stylist, instead of the Claude option discussed. The requested 1.5 Flash is replaced by a current Flash model, pending verification.
- **Accepted wiring:** one synchronous upload request; images on disk with paths in Postgres; email + password accounts (BCrypt) with a session token stored in SharedPreferences; everything runs locally and the demo is recorded from the emulator.

**Implementation details derived by the agent** (changeable): opaque DB-stored session tokens + `AuthInterceptor` rather than Spring Security/JWT; Gradle for the backend; Retrofit/Glide/ViewPager2; the REST paths above; the random outfit on the device; "tagging failure still saves the item"; the stylist total-failure message; allowing null slots when saving.

**Learning focus (from `learner-profile.md > Desired Learning Outcome`: "seamlessly integrating AI features"):** the concept worked through here is **how to make an AI feature reliable in a backend**. The model's answer is treated as *untrusted input*: it's constrained to a JSON schema, then validated against the database (ownership + category) with a deterministic fallback. Evidence in the build: `StylistServiceTest` covers a foreign id, a wrong-category id and an empty category, and a manual check shows a hint with no match still producing a full look. The learner didn't name a specific personal uncertainty during this interview.

**To verify early in the build** (not blocking approval):
1. The current Gemini Flash model id, the free-tier limits, and the Java SDK calls for image input + JSON schema.
2. The rembg server command, endpoint and field name (one `curl` test).
3. The current Spring Boot 3.x version.

**Carried from `prd.md > Open Questions`:**
- Outerwear in AI looks → optional, the stylist may add one (resolved above).
- Log In restores that account's wardrobe → yes (`POST /api/auth/login` with email + password).
- Home tab = wardrobe → yes (`WardrobeFragment`).
