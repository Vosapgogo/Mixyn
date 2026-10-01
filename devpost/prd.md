---
doc: prd
status: approved
---

# Mixyn — Product Requirements

An AI stylist that builds outfits from the clothes you actually own — for someone who cares about a versatile wardrobe and needs a quick decision on bad-mood mornings — and for anyone who wants to shop less and wear more of what they already own.
Source: `scope.md > The Unique Kernel`, `scope.md > Who It's For`.

## The Core Journey
Source: `scope.md > The Core Loop`, `scope.md > What "Working" Looks Like`.

1. **Open the app.** A splash/loading screen appears every time.
2. **First visit:** the Welcome screen shows the Mixyn logo and name, a short description, and **Create Account** / **Log In**.
3. **Create Account:** enter Name, Email, Password, and City → the account is created in the database → a welcome/success message → land on the main screen.
4. **Empty wardrobe:** the Clothes tab shows the empty-wardrobe illustration, "Your closet is empty," and **+ Add clothes**.
5. **Add clothes:** tap the central **+** → **Add clothes** → pick a photo from the gallery → processing screen shows "Background removed · 1/1 completed" → **View item**.
6. **Item card:** the item on a clean white background. The AI has filled in **Category** and **Color**; the user can correct them and optionally add a name, tags, and season.
7. Repeat until the wardrobe has tops, bottoms, shoes (and optionally outerwear).
8. **Create outfit:** tap **+** → **Create outfit**. A random outfit from the wardrobe is already on the canvas.
9. **Mix and match:** swipe left or right on any single piece to change only that piece.
10. **Generate with AI:** optionally type a hint ("burgundy", "this oversized shirt") → tap **Generate with AI** → the canvas is replaced with an AI-picked look from the user's own items.
11. **Save:** tap **Save** → the outfit appears in the **Outfits** tab.
12. **Returning visit:** splash → straight to the main screen, with wardrobe and saved outfits still there (loaded from the database).
13. **Log out and back in:** Profile → **Log out** → Welcome. **Log In** with email + password → the same wardrobe and outfits come back.

Success = step 11: an outfit the user likes, built from their own clothes, saved.

## Screens and Layout

- **Splash** — loading screen on every launch.
- **Welcome** — logo, "Mixyn," one-line description, **Create Account** and **Log In** buttons. Shown only when no session exists.
- **Create Account** — Name, Email, Password, City. Success message after creating.
- **Log In** — Email + Password.
- **Main screen (Wardrobe)** — two tabs: **Clothes** (item grid, or the empty state) and **Outfits** (saved outfits).
- **Bottom navigation** — Home, Calendar, large black central **+**, Friends, Profile. Calendar and Friends are visible but disabled, marked "coming soon."
- **Profile** — shows the user's name, email, and city, plus **Log out**. Everything else on it is "coming soon."
- **+ menu** — Add clothes, Create outfit, OutfitCHECK (OutfitCHECK visible but disabled, "coming soon").
- **Add clothes flow** — gallery picker → processing screen → **View item**.
- **Item card** — large item image on white; name field ("Add a name…" + Edit); Category (e.g. "Outerwear >"); Tags (Casual, Night out, Streetwear, Date night, Work, Favorites, Sports, Cozy); Color (swatch + name, e.g. "Khaki"); Season.
- **Create outfit (canvas)** — white canvas with pieces stacked vertically like a silhouette: Top, Bottom, Shoes; Outerwear overlaps the Top. A hint text field + **Generate with AI**. **Save** at the bottom.

## Look and Feel
- **Mood:** Japandi — minimalist, clean, functional, but warm, cozy, and calming. Like a curated Instagram feed or a digital fashion magazine, not a heavy tech app.
- **Colors:** warm beige / off-white / light sand background; clean white cards and canvas so background-removed items pop; dark charcoal / soft black for text and primary buttons (softer than pure `#000`). No brand accent color — the clothes' own earthy tones (camel, khaki, denim, rust) are the accents.
- **Type:** elegant classic serif for the Mixyn logo and main headers (editorial, high-fashion); legible modern sans-serif for buttons, tags, and body.
- **References:** the clean, structural UI of the COS and Zara apps + the warm, effortless feel of minimalist lifestyle creators on Pinterest/Instagram.
- **Signature details:** the white open-wardrobe illustration with empty hangers and closed drawers; the big black central **+**.

## Features and Behavior

### Account and Session
Source: `scope.md > The POC Boundary` ("Real accounts from the start").
Real accounts backed by a database — built from the start, not added later.
- [ ] First launch with no session shows Splash → Welcome with Create Account / Log In.
- [ ] Create Account takes Name, Email, Password, and City; after submitting, the account exists in the database, a welcome/success message appears, then the main screen.
- [ ] Passwords are never stored or shown in plain text.
- [ ] Log In with the correct email + password opens that user's own wardrobe and outfits.
- [ ] Wrong email or password shows a friendly error ("Email or password is incorrect"); nothing else happens.
- [ ] Creating an account with an email that already exists shows a friendly error and suggests Log In.
- [ ] Closing and reopening the app goes Splash → main screen directly, skipping Welcome (session persists).
- [ ] Profile → **Log out** ends the session and returns to Welcome.
- [ ] Each user sees only their own clothes and outfits — never another user's.
- [ ] City is stored with the user (for future weather), even though no weather is shown yet.

### Adding Clothes
Source: `scope.md > The POC Boundary` ("Upload clothes with AI background removal").
- [ ] Empty Clothes tab shows the wardrobe illustration, "Your closet is empty," "Add your first piece. Snap a photo and we'll clean up the background for you," and **+ Add clothes**.
- [ ] Central **+** opens a menu containing **Add clothes** and **Create outfit**; OutfitCHECK is shown but disabled with "coming soon."
- [ ] Calendar and Friends in the bottom nav are visible but disabled with "coming soon"; tapping them doesn't navigate anywhere.
- [ ] Add clothes lets the user pick a photo from the gallery (camera not required for the PoC).
- [ ] A processing screen shows progress, then "Background removed · 1/1 completed" and a **View item** button.
- [ ] The item card shows the photo with its original background fully removed, on a clean light background.
- [ ] The item appears in the Clothes tab grid afterward and is saved to the user's account in the database.
- [ ] One photo is added at a time: choosing several in the gallery shows "One photo at a time" and uploads nothing.
- [ ] A photo that isn't a single clothing item (a person wearing clothes, several items, not clothing) is not added; the screen explains "Only one item, please" and offers to choose another photo.

### AI Item Details
Source: `scope.md > The Unique Kernel`.
- [ ] After upload, **Category** is filled automatically with one of: Tops, Bottoms, Shoes, Outerwear.
- [ ] After upload, **Color** is filled automatically (swatch + color name).
- [ ] The user can change Category and Color if the AI got them wrong; the change sticks.
- [ ] Name, Tags, and Season are optional manual fields; leaving them empty is fine.
- [ ] The item card has a **rotate** option, so a photo that came out upside down or sideways can be turned upright by the user (the AI does not rotate photos).

### Outfit Canvas and Carousel
Source: `scope.md > The Core Loop`.
- [ ] Opening Create outfit shows a random outfit from the user's wardrobe: one Top, one Bottom, one Shoes, stacked vertically.
- [ ] Outerwear, when present, appears as a layer overlapping the Top.
- [ ] Swiping left/right on a piece changes only that piece to another item of the same category; all other pieces stay.
- [ ] If a category has no items, its slot shows a faded placeholder outline with a small **+** and "Add shoes to complete the look" (wording adapted to the category).

### AI Stylist
Source: `scope.md > The Unique Kernel`, `scope.md > What "Working" Looks Like` (the "aha" beat).
- [ ] **Generate with AI** with an empty hint replaces the canvas with a complete AI-picked look (Top, Bottom, Shoes) using only items in the user's wardrobe.
- [ ] With a hint (e.g. "burgundy"), the generated look favors items matching that color or vibe; with a specific-item hint ("this oversized shirt"), the look is built around that item.
- [ ] If nothing matches the hint, a friendly toast appears — "We couldn't find anything burgundy. Here is another stylish option!" — and a look from available items is shown anyway.
- [ ] The AI never places an item the user doesn't own.
- [ ] Missing categories show the same placeholder as the carousel.

### Saving Outfits
Source: `scope.md > The Core Loop`.
- [ ] **Save** on the canvas stores the current outfit (manual or AI-generated).
- [ ] The saved outfit appears in the **Outfits** tab, showing its pieces.
- [ ] Saved outfits are still there after closing and reopening the app, and after logging out and back in.

## States and Boundaries
- **First use** — no session → Welcome; empty wardrobe → illustrated empty state.
- **Returning use** — session exists → skip Welcome; wardrobe and outfits load from the database.
- **Logged out** — Welcome screen; Log In restores the same account's data.
- **Wrong credentials / email already taken** — friendly error on the form; no crash, no partial account.
- **Database or network unavailable** — *Assumption:* a friendly "Can't connect right now, try again" message; nothing is silently lost.
- **Processing** — background removal shows a progress/processing screen; the user isn't left on a frozen screen.
- **Partial wardrobe** — missing categories show a placeholder slot with **+**, both in manual and AI modes.
- **Unmatched hint** — friendly toast + fallback look; never a blank canvas or error.
- **AI mislabels an item** — user corrects Category/Color on the item card.
- **Only one item in a category** — *Assumption:* swiping that piece does nothing (or stays on the same item); no error.
- **Photo can't be processed** (unreadable image, AI failure) — *Assumption:* a friendly error on the processing screen with an option to try again; nothing half-saved appears in the wardrobe.

## Product Decisions
- **Real accounts and a real database from the start** — the hackathon runs a full month (Sept 22 – Oct 26, 2026), so there is time to do it properly; it matches the learner's goal of "managing data" and a daily-use app, and makes the product feel complete, not a mock.
- **Create Account asks Name, Email, Password, City** — email + password for a real login; name personalizes; city is kept for future weather.
- **Every item and outfit belongs to one user** — data is private per account, in line with the private, friends-only vision.
- **AI fills only Category and Color** — "the absolute minimum needed for the core engine": category places items in the carousel, color lets the stylist answer hints. User can always correct them.
- **Name, Tags, Season are optional manual fields.**
- **PoC categories: Tops, Bottoms, Shoes, Outerwear** — outerwear "proves the app's layering logic." Hats and Bags deferred.
- **Swipe per piece** — changing pants doesn't disturb the rest of the outfit.
- **Graceful AI fallbacks** — an unmatched hint still produces a look; a missing category shows a placeholder, not a failure.
- **Save works; "Wear it today" doesn't (yet)** — Save proves "the creation loop is complete and the data is stored."
- **Gallery upload only** for the demo.
- **Deferred features stay visible as "coming soon"** — Calendar, Friends, and OutfitCHECK appear disabled; Profile holds only account info and Log out, hinting at the full vision without building it.
- **Japandi look** — warm neutrals, white canvas, serif headers, clothes as the accent.

## What We're Building
1. Splash, Welcome, and real Create Account / Log In (email + password) backed by a database, with a persistent session and Log out.
2. Database storing users, clothing items, and outfits, each tied to its owner.
3. Wardrobe main screen with Clothes and Outfits tabs, including the illustrated empty state.
4. Add clothes from gallery with real AI background removal and a processing → View item flow.
5. Item card with AI-filled Category and Color (editable) and optional Name, Tags, Season.
6. Create outfit canvas: random starting outfit, per-piece swiping, outerwear layer, missing-category placeholders.
7. AI stylist: one-click generation with an optional hint, only from owned items, with the unmatched-hint fallback.
8. Save outfit → Outfits tab, persisting across sessions.
9. Japandi styling across all of the above.

## Deferred From the POC
- **Password reset, email verification, Google/Apple sign-in** — nice to have; email + password is enough to prove real accounts.
- **Live weather by city** — deferred in scope; city is stored for later.
- **Hats and Bags** — hats depend on weather triggers; bags don't demonstrate layering as strongly as outerwear.
- **Camera capture** — gallery upload is enough for the demo.
- **"Wear it today," Calendar, and streaks** — gamification, not the styling engine.
- **Collections** (Work, Casual, Date night) on saved outfits — saving to the Outfits tab proves the loop.
- **OutfitCHECK, Friends, sharing, ratings, leaderboard** — social layer deferred in scope.
- **AI auto-filling Tags, Season, and Name** — manual for now.

## Possible Later Enhancements
- Weather-aware suggestions, including cold/hot triggers for outerwear and hats.
- Accessories step with bags on the canvas.
- AI stylist that uses tags and season, not just category and color.
- Everything in `scope.md > Later`: social, calendar, Group Vibes, Wardrobe Swap, Travel Mode, Cost-per-Wear, Avatar Mannequin.

## Non-Goals
- **No public feed or public profiles** — the social vision is private and friends-only, and not in this PoC anyway.
- **No "one month later" time-skip demo** — cut in scope; it doesn't prove the styling engine.
- **No shopping or recommending new clothes** — Mixyn works only with what you own (mindful consumption).
- **No production deployment requirement** — deployment is optional for the hackathon.

## Open Questions
- **Does the AI stylist include Outerwear in generated looks,** or only Top/Bottom/Shoes? *Assumption:* optional — it may add outerwear if the user has some. Can confirm before `4-spec`.
- **Home tab vs Wardrobe:** is "Home" in the nav the same as the wardrobe main screen? *Assumption:* yes.
