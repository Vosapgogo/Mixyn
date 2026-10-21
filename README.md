# Mixyn

An Android app that keeps a digital copy of your wardrobe and builds outfits only from the clothes you
actually own, with an AI stylist powered by Google Gemini.

Mixyn is a proof of concept built for the Devpost hackathon *Build With AI: Basics* (September–October 2026).
It runs locally: an Android app on the emulator talks to a Spring Boot backend on your computer.

## What it does

- **Accounts:** sign up with name, email, password and city; log in and out. The profile has a photo, a
  username and the city used for the weather, and lets you change the password or delete the account.
- **Add clothes:** pick a photo from the gallery. The background is removed locally (rembg), and Gemini
  names the piece: its type from a fixed list of 47 (Blazer, Jeans, Sneakers…), its category and up to
  three colors from a fixed palette. A photo that isn't one piece (a whole outfit, several pieces, no
  clothing) is turned away with an explanation.
- **Item card:** correct the type, colors, tags, seasons and name; rotate, mirror or resize the photo;
  mark a top as worn open or closed; remove the piece from the wardrobe.
- **Create outfit:** a canvas that starts with a random look from your closet. Swipe the top, bottoms or
  shoes to change just that piece, and add layers: something under the top, outerwear, a bag,
  accessories. A slot you have nothing for shows a placeholder that opens Add clothes. The weather in
  your city is shown, and in the cold or heat the app offers the layers your closet has.
- **Ask AI:** the stylist builds a whole look from your own pieces, with or without a request
  ("dinner date in the cold"), and remembers the earlier requests so follow-ups change only what you ask.
- **Stylist chat:** ask for several outfits for an occasion, pieces of a color, what you haven't worn for
  a while, or a description of your style. Every look can be opened on the canvas.
- **Saved outfits and calendar:** save a look (it needs a top, bottoms and shoes, or a dress and shoes),
  edit it later, and mark it as worn today. The Calendar tab shows what you wore on each day.
- **Search and filters** for clothes and outfits, and a weather page with the next hours and 7 days.

The backend never trusts the AI blindly: every item id a model returns is checked against the user's
own wardrobe and the slot it is meant for, and anything invalid is replaced or dropped. If Gemini is
unavailable, uploads still work (the piece is saved without tags) and the stylist falls back to a random
look made of your clothes.

## How it fits together

```
 Android app ──HTTP (10.0.2.2:8080)──▶ Spring Boot backend ──▶ PostgreSQL (users, items, outfits, calendar)
                                          │  ├──▶ item and outfit pictures (PNG files)
                                          │  ├──▶ rembg container (background removal, local)
                                          │  ├──▶ Gemini API (tagging, stylist, chat)
                                          │  └──▶ Open-Meteo (city search and weather, no key)
```

| Part | Technology |
|---|---|
| Android app | Java, XML layouts, minSdk 26, targetSdk 36; Retrofit, Glide, Material Components, ViewPager2 |
| Backend | Spring Boot 4.1, Java 21, Gradle, Spring Data JPA, BCrypt password hashes |
| Database | PostgreSQL 16 (Docker) |
| Background removal | [rembg](https://github.com/danielgatis/rembg) in its own Docker image, models u2net and isnet-general-use |
| AI | Google Gemini Flash models through the Google Gen AI Java SDK, with JSON-schema answers |
| Weather and cities | [Open-Meteo](https://open-meteo.com/) forecast and geocoding APIs |

## Repository layout

```
android/              Android app (open this folder in Android Studio)
backend/              Spring Boot backend
rembg/                Dockerfile of the background-removal service
scripts/              backup.sh and restore.sh for the wardrobe data
devpost/              planning documents: scope, PRD, technical spec, build checklist
docker-compose.yml    database, rembg and backend
.env.example          settings to copy into .env
```

## Requirements

- **Docker Desktop.** The images take about 3 GB of disk (the rembg image alone is about 2 GB).
- **JDK 21 or newer**, to run the backend outside Docker and to run its tests.
- **Android Studio** with Android SDK Platform 37 and an emulator (API 26 or newer; developed on a Pixel 6
  image with API 37).
- **A Gemini API key.** A free key from [Google AI Studio](https://aistudio.google.com/apikey) is enough.

## Setup

```bash
git clone https://github.com/Vosapgogo/Mixyn.git
```

```bash
cd Mixyn
```

```bash
cp .env.example .env
```

Open `.env` and set `GEMINI_API_KEY`. You may also change `POSTGRES_PASSWORD`. `.env` is git-ignored;
never commit it. `GEMINI_MODEL` lists the Gemini models to use, best first; the defaults work on the free
tier.

## Run the backend

Choose one of the two ways and stick with it: both use the same database, but they keep the pictures in
different places (a Docker volume, or `backend/data/images`), so switching between them makes the
pictures of earlier pieces go missing.

### Option A: everything in Docker

```bash
docker compose up --build
```

The first build takes several minutes: it downloads the two background-removal models (about 170 MB
each) into the rembg image and compiles the backend. It starts:

| Service | Port | Notes |
|---|---|---|
| `db` | 5432 | PostgreSQL 16, data in the `pgdata` volume |
| `rembg` | 7010 | not 7000, which macOS uses for AirPlay |
| `backend` | 8080 | pictures in the `images` volume |

### Option B: backend on your computer (faster restarts while developing)

Start the database and rembg in Docker:

```bash
docker compose up -d db rembg
```

Then run the backend (it reads `../.env`; pictures go to `backend/data/images`):

```bash
cd backend && ./gradlew bootRun
```

### Check that it is up

```bash
curl http://localhost:8080/api/health
```

It should answer `{"status":"ok"}`.

## Run the Android app

Open the `android/` folder in Android Studio, wait for Gradle to sync, start an emulator and run the
`app` configuration. Or, with an emulator already running:

```bash
cd android && ./gradlew installDebug
```

- The app calls the backend at `http://10.0.2.2:8080/`, which is the emulator's address for your computer.
  Plain HTTP is allowed only to that address (`res/xml/network_security_config.xml`), so the app runs on
  the emulator, not on a physical phone, without changes.
- To use a backend on another port, build with `./gradlew installDebug -PapiPort=8081`.
- To have photos to upload, drag image files onto the emulator window; they land in its gallery.

## Tests

```bash
cd backend && ./gradlew test
```

The backend has unit tests (no database, no network) for the rules that matter most: the validation of
the stylist's and the chat's answers, saving and changing outfits, the calendar, the tagging and cutout
logic, the race between Gemini models, the weather, and the account rules.

## Backup and restore

The wardrobe (the database and the pictures) is not in git. With Option B:

```bash
scripts/backup.sh
```

saves both into one dated archive (by default in iCloud Drive, folder "Mixyn backups"; pass a folder to
put it elsewhere). To put a backup back, stop the backend first, then run
`scripts/restore.sh <archive.tar.gz>`. Restoring replaces the database.

## Troubleshooting

- **The app can't reach the server.** Check that the backend answers on `localhost:8080` and that `db`
  and `rembg` are running (`docker ps`). Docker Desktop can stop idle containers; start them again with
  `docker compose up -d db rembg`.
- **A new piece has no type or colors, or the stylist says it is taking a break.** Gemini didn't answer in
  time: on the free tier the models are sometimes overloaded, and each has a daily quota. The backend asks
  the next model in `GEMINI_MODEL` when one is slow or fails. Set the type on the item card, or try again
  later.
- **The first upload after a start is slower.** rembg loads its model into memory on the first request.
- **The Docker build fails with "no space left on device".** Free some disk space; the rembg image is large.

## Planning documents

The `devpost/` folder shows how the project was planned and built: `scope.md` (what the proof of concept
covers), `prd.md` (product behavior), `spec.md` (technical design) and `checklist.md` (the build in slices,
with every change of plan recorded under Revisions).

## Credits

- Fonts Instrument Serif and Inter (SIL Open Font License) and Material Symbols icons (Apache 2.0); see
  `android/THIRD_PARTY_NOTICES.md`.
- [rembg](https://github.com/danielgatis/rembg) (MIT) with the u2net and isnet-general-use models.
- Weather and city data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
- AI by Google Gemini.
