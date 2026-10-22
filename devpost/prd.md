---
doc: prd
status: approved
---

# Mixyn — Product Requirements

An app for the clothes you already own: you put outfits together from your own wardrobe, an AI assistant on the outfit screen builds or improves the look, and a stylist chat helps with your style as a whole — for someone who cares about a versatile wardrobe and needs a quick decision on bad-mood mornings, and for anyone who wants to shop less and wear more of what they already own.
Source: `scope.md > The Idea`, `scope.md > The Unique Kernel`, `scope.md > Who It's For`.

## The Core Journey
Source: `scope.md > The Core Loop`, `scope.md > What "Working" Looks Like`.

1. **Open the app.** A splash/loading screen appears every time.
2. **First visit:** the Welcome screen shows the Mixyn logo and name, a short description, and **Create Account** / **Log In**.
3. **Create Account:** enter Name, Email, Password, and City → the account is created in the database → a welcome/success message → land on the main screen.
4. **Empty wardrobe:** the Clothes tab shows the empty-wardrobe illustration, "Your closet is empty," and **+ Add clothes**.
5. **Add clothes:** tap the central **+** → **Add clothes** → pick a photo from the gallery → processing screen shows "Background removed · 1/1 completed" → **View item**.
6. **Item card:** the item on a clean light background. The AI has filled in its **type** (which sets the category) and up to three **colors**; the user can correct them and optionally add a name, tags, and seasons.
7. Repeat until the wardrobe has tops, bottoms, shoes (and optionally outerwear, bags and accessories).
8. **Create outfit:** tap **+** → **Create outfit**. A random outfit from the wardrobe is already on the canvas.
9. **Mix and match:** swipe left or right on any single piece to change only that piece.
10. **Ask AI:** tap the round **Ask AI** button on the canvas → optionally describe what you want ("burgundy", "this oversized shirt", "dinner date in the cold") → **Generate look** → the canvas is replaced with an AI-picked look from the user's own items.
11. **Save:** tap **Next** → "Your fit for today" shows the look with its pieces, colors, category and seasons → **Save** → the outfit appears in the **Outfits** tab. **Wear it today** saves it too and puts it on today in the **Calendar**.
12. **Returning visit:** splash → straight to the main screen, with wardrobe and saved outfits still there (loaded from the database).
13. **Log out and back in:** Profile → **Log out** → Welcome. **Log In** with email + password → the same wardrobe and outfits come back.
14. **Stylist chat:** **Stylist** in the bottom bar → ask "3 outfits for a date" or "how can I improve my style?" → the stylist answers with advice and outfit cards made of the user's clothes; a card opens on the canvas.

Success = step 11: an outfit the user likes, built from their own clothes, saved.

## Screens and Layout

- **Splash** — loading screen on every launch.
- **Welcome** — logo, "Mixyn," one-line description, **Create Account** and **Log In** buttons. Shown only when no session exists.
- **Create Account** — Name, Email, Password, City. Success message after creating.
- **Log In** — Email + Password.
- **Main screen (Wardrobe)** — two tabs: **Clothes** (item grid, or the empty state) and **Outfits** (saved outfits). In the header, a magnifier opens **search** and the three lines open the **filter** of the tab that is showing.
- **Bottom navigation** — Home, Calendar, large black central **+**, Stylist, Profile. (Friends had this place in the design; it is the next step after the PoC.)
- **Calendar** — one month at a time; a day on which an outfit was worn shows that outfit's picture, today has a frame. The days of the neighboring months fill the first and last week on beige. A calendar icon opens a month and year picker.
- **Profile** — the photo, name, @username · city, three tiles (pieces, outfits saved, days worn), the closet's palette and most used tags, **City for weather** (opens the weather page), **Log out** and **Delete account**; the pencil opens **Edit profile**.
- **Weather page** — the city's weather now, advice for today, the next hours and the next 7 days.
- **Stylist** — a chat with the AI stylist, with suggested questions to start.
- **+ menu** — Add clothes and Create outfit.
- **Add clothes flow** — gallery picker → processing screen → **View item**.
- **Item card** — large item image; name ("Add a name…" + pencil) and **Edit** for the photo; Type with its category (e.g. "Blazer · Outerwear >"); Tags (Casual, Night out, Streetwear, Date night, Work, Favorites, Sports, Cozy); Colors (dots + names, e.g. "Khaki, Brown"); Seasons; Open / Closed front for tops and outerwear; **Remove from wardrobe**.
- **Create outfit (canvas)** — pieces stacked vertically like a silhouette: Top, Bottom, Shoes, with layers around them (a piece under the top, outerwear over its left half, a bag, accessories in their places). The header has back, a filter, the weather chip and **Next**; the bottom panel has the tiles **Under layer**, **Outerwear**, **Bag**, **Accessories** and the round **Ask AI** button. **Next** leads to the outfit card.
- **Outfit card** — "Your fit for today" with the date and weather; the look on a white card with the "mixyn" mark; then, like the item card, the clothes in the outfit (names), its colors, its category and its seasons; **Save** and **Wear it today**. A saved outfit opens as the same card, with **Delete outfit**.

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
- [ ] Profile → **Log out** asks "Log out?", ends the session and shows a goodbye screen ("You're logged out. We hope to see you again soon.") with a way back to Welcome.
- [ ] Each user sees only their own clothes and outfits — never another user's.
- [ ] City is stored with the user, with its coordinates, and gives the weather on the canvas and the weather page.

### Profile
Source: learner decision on 2026-10-20, after the Stylist chat (design page 19), with a cut-off on the evening of 23 October; after that only checks and the submission.
- [ ] Profile shows the avatar (or a placeholder), the name, @username · city, three tiles (pieces, outfits saved, days worn), the closet's palette (the colors of the user's clothes) and the tags used most.
- [ ] The pencil opens **Edit profile**: photo (from the gallery), name, username, city (with suggestions, as at sign-up) and a new password (with the current one). Wrong input shows an error under its field, as at sign-up; a username someone else has says so.
- [ ] The avatar is kept with the account, so friends can see it once friends exist (the next step, see Possible Later Enhancements).
- [ ] **City for weather** shows the city and today's weather there, and lets the user change the city.
- [ ] **Delete account** asks "Delete your account?" (everything goes: clothes, outfits, calendar) and then removes the account and all its data; a goodbye screen follows, as after logging out.
- [ ] Streaks, the morning outfit reminder and "who sees my fits" are later (they need the calendar streaks, notifications and friends).

### Adding Clothes
Source: `scope.md > The POC Boundary` ("Upload clothes with AI background removal").
- [ ] Empty Clothes tab shows the wardrobe illustration, "Your closet is empty," "Add your first piece. Snap a photo and we'll clean up the background for you," and **+ Add clothes**.
- [ ] Central **+** opens a menu containing **Add clothes** and **Create outfit**.
- [ ] **Stylist** in the bottom nav opens the Stylist chat (Friends, which had this place in the design, is the next step after the PoC).
- [ ] Add clothes lets the user pick a photo from the gallery (camera not required for the PoC).
- [ ] A processing screen shows progress, then "Background removed · 1/1 completed" and a **View item** button.
- [ ] The item card shows the photo with its original background fully removed, on a clean light background.
- [ ] The item appears in the Clothes tab grid afterward and is saved to the user's account in the database.
- [ ] One photo is added at a time: choosing several in the gallery shows "One photo at a time" and uploads nothing.
- [ ] A photo that isn't a single clothing item (a person wearing clothes, several items, not clothing) is not added; the screen explains "Only one item, please" and offers to choose another photo.

### AI Item Details
Source: `scope.md > The Unique Kernel`.
- [ ] After upload, the **type** is filled automatically, and with it the **category**: Top, Bottom, Shoes, Outerwear, Bag or Accessory.
- [ ] After upload, up to three **colors** are filled automatically (dots + color names).
- [ ] The user can change the type and the colors if the AI got them wrong; the change sticks.
- [ ] Name, Tags, and Seasons are optional manual fields; leaving them empty is fine. Several seasons can be chosen (all four, or "All", is saved as "All").
- [ ] The item card's **Edit** button opens "Edit photo": size (50–150%), rotate left/right 90°, mirror, Reset and Done, with a live preview; the AI does not rotate photos.
- [ ] Each item has a **type** from a fixed list (Blazer, Jeans, Sneakers…). The AI picks it at upload; the user changes it by typing the first letters and choosing a suggestion. The type decides the big category (Top / Bottom / Shoes / Outerwear / Bag / Accessory), and the outfit canvas works by the big category, so every kind of outerwear shows up in the Outerwear layer.
- [ ] An item can have up to **three colors** from a fixed palette, shown as dots with names ("Brown, White"). The AI picks them at upload; the user changes them with chips, like tags.

### Outfit Canvas and Carousel
Source: `scope.md > The Core Loop`.
- [ ] Opening Create outfit shows a random outfit from the user's wardrobe: one Top, one Bottom, one Shoes, stacked vertically.
- [ ] Outerwear appears as half a jacket over the left side of the Top, so the top stays visible. It is put on with the **Outerwear** tile (or by the stylist); **Under layer**, **Bag** and **Accessories** work the same way, and a layer the closet has nothing for offers to add one.
- [ ] Swiping left/right on a piece changes only that piece to another item of the same category; all other pieces stay.
- [ ] If a category has no items, its slot shows a faded placeholder outline with a small **+** and "Add shoes to complete the look" (wording adapted to the category).
- [ ] A filter on the canvas narrows each carousel to the **Type** and **Color** chosen for its category (Tops: sweater, red; Bottoms: trousers, blue); new choices add to the ones made before. A slot the filter leaves empty says "No shoes match your filter"; the filter can be cleared at any time and is gone when the canvas is closed.

### AI Stylist
Source: `scope.md > The Unique Kernel`, `scope.md > What "Working" Looks Like` (the "aha" beat).
- [ ] **Ask AI** with an empty request replaces the canvas with a complete AI-picked look (Top, Bottom, Shoes, and layers when they suit it) using only items in the user's wardrobe.
- [ ] With a hint (e.g. "burgundy"), the generated look favors items matching that color or vibe; with a specific-item hint ("this oversized shirt"), the look is built around that item.
- [ ] The request can be a full description or an occasion ("job interview", "first date", "gym"), in any language; the stylist uses each item's type, tags, colors and seasons to choose.
- [ ] The stylist remembers the earlier requests of the current styling session, so a follow-up like "other shoes" or "warmer" changes only what was asked; **Start over** forgets them.
- [ ] The outfit screen shows the current weather in the user's city ("13° · cool"), and the stylist dresses for it unless the request sets another context.
- [ ] If nothing fits the request, a friendly toast appears — "Nothing in your closet quite fits “burgundy”. Here is another stylish option!" — and a look from available items is shown anyway.
- [ ] The AI never places an item the user doesn't own.
- [ ] Missing categories show the same placeholder as the carousel.

### Saving Outfits
Source: `scope.md > The Core Loop`.
- [ ] **Next** on the canvas opens "Your fit for today": the look as on the canvas (layers included), the date and the weather.
- [ ] Every outfit has a top, bottoms and shoes; the layers are optional. Without one of the three, Next says which is missing ("Add shoes to complete the look") and offers to add it, and the backend refuses to save or update it.
- [ ] A dress replaces the bottoms: on the canvas it hangs over the place of the bottoms like a long coat, and the bottoms carousel starts on an empty page; swiping it still offers leggings or jeans to wear under the dress. A dress and shoes are a complete outfit.
- [ ] Under the picture the card lists the clothes in the outfit by name, the outfit's colors (taken from its clothes), and lets the user choose its category (Casual, Work, Date night…) and its seasons.
- [ ] **Save** stores the outfit (manual or AI-generated) and opens the **Outfits** tab, where it appears as a picture; tapping it opens its card.
- [ ] On a saved outfit's card the category and seasons can be changed, and **Delete outfit** removes it after asking "Delete this outfit?".
- [ ] **Wear it today** saves the outfit and puts it on today in the **Calendar**; on an already saved outfit it only puts it on today. A day holds one outfit: wearing another one replaces it.
- [ ] The Calendar shows the month with the worn outfits on their days; tapping one opens its card.
- [ ] Saved outfits and the calendar are still there after closing and reopening the app, and after logging out and back in.
- [ ] Removing a piece from the wardrobe takes it out of the saved outfits that had it; the outfits stay.
- [ ] **Edit** on a saved outfit opens the canvas with it on; after Next the user chooses **Save as new** (the outfit stays as it was) or **Update** (the same outfit gets the new look).
- [ ] Days on which an outfit was already worn keep showing it as it was, also after it is updated; such a day opens the outfit "As you wore it", which can't be changed and can open the outfit as it is now. Deleting the outfit removes those days too.
- [ ] The same outfit (the same pieces in the same places) can't be saved twice; the user is told and can open the one that exists.
- [ ] Changing a piece's type on its card checks the saved outfits: where the piece can't be worn in its place any more and its new place is free, it moves there; if that place is taken (a T-shirt turned skirt, in an outfit with jeans), an error names the outfit and nothing changes.
- [ ] In the calendar, the cells before the 1st and after the last day show the neighboring months' dates on beige and can't be tapped; the calendar icon (or the month's name) opens a picker with a year and its months, and months that haven't come yet can't be chosen.

### Stylist Chat
Source: learner decision on 2026-10-20 (a light version, with a cut-off on the evening of 22 October; otherwise a plain Stylist button that opens Ask AI).
- [ ] **Stylist** in the bottom bar (in place of Friends, the next step after the PoC) opens a chat with the AI stylist.
- [ ] The user writes a request in their own words and language: "3 outfits for a date", "what goes well with this sweater", "show all my black pieces", "which pieces suit me best", "what haven't I worn for a long time", "describe my style from my clothes", "how can I improve my style" (tips only with what they own, never buying new clothes).
- [ ] The stylist answers with a short message and, where it helps, up to 3 outfit cards made only of the user's own clothes (each a complete outfit), or pictures of the matching pieces (up to 12).
- [ ] An outfit card opens on the canvas, where it can be changed and saved like any look; a piece opens its item card.
- [ ] The chat remembers the conversation while it is open, so follow-ups work ("make the second one warmer").
- [ ] A message that isn't about clothes or style gets only: "I help only with choosing clothes and your style."
- [ ] Chat history and trying outfits on the user's photo are later (see Possible Later Enhancements).

### Search and Filters
Source: learner request while checking saved outfits.
- [ ] The magnifier on Home opens a search field: typing part of a piece's name (the one the user gave it, or its color and type) or of its type narrows the Clothes grid; on the Outfits tab it shows the outfits that have such a piece.
- [ ] The three lines open the filter of the tab that is showing, and carry a dot while a filter is on.
- [ ] Clothes are filtered by color, type (Blazer, Jeans…), tags (Work, Date night…) and season. Inside a group one hit is enough; a piece must fit every group that has a choice.
- [ ] Outfits are filtered by the colors and the kinds of piece of their clothes — an outfit must have every chosen color and every chosen kind — and by their category and season, where one of the chosen ones is enough. A piece or an outfit marked for all seasons fits any season.
- [ ] When nothing fits, the tab says "Nothing matches" and offers **Clear filters**.

## States and Boundaries
- **First use** — no session → Welcome; empty wardrobe → illustrated empty state.
- **Returning use** — session exists → skip Welcome; wardrobe and outfits load from the database.
- **Logged out** — Welcome screen; Log In restores the same account's data.
- **Wrong credentials / email already taken** — friendly error on the form; no crash, no partial account.
- **Database or network unavailable** — *Assumption:* a friendly "Can't connect right now, try again" message; nothing is silently lost.
- **Processing** — background removal shows a progress/processing screen; the user isn't left on a frozen screen.
- **Partial wardrobe** — missing categories show a placeholder slot with **+**, both in manual and AI modes.
- **Unmatched hint** — friendly toast + fallback look; never a blank canvas or error.
- **AI mislabels an item** — user corrects the type and colors on the item card.
- **Only one item in a category** — swiping that piece does nothing; no error.
- **Photo can't be processed** (unreadable image, background removal fails) — a friendly error on the processing screen with **Try again**; nothing half-saved appears in the wardrobe. If only the AI is unavailable, the piece is saved without a type, and the item card asks for one.

## Product Decisions
- **Real accounts and a real database from the start** — the hackathon runs a full month (Sept 22 – Oct 26, 2026), so there is time to do it properly; it matches the learner's goal of "managing data" and a daily-use app, and makes the product feel complete, not a mock.
- **Create Account asks Name, Email, Password, City** — email + password for a real login; name personalizes; the city gives the weather.
- **Every item and outfit belongs to one user** — data is private per account, in line with the private, friends-only vision.
- **AI fills the type and the colors** — the minimum the core engine needs: the type places items in the carousel, the colors let the stylist answer hints. Both come from fixed lists (47 types, an 18-color palette), and the user can always correct them.
- **Name, Tags, Season are optional manual fields.**
- **Categories: Tops, Bottoms, Shoes, Outerwear, Bags, Accessories** — outerwear "proves the app's layering logic"; bags and accessories came in with the canvas layers during the build (learner decision).
- **Swipe per piece** — changing pants doesn't disturb the rest of the outfit.
- **Graceful AI fallbacks** — an unmatched hint still produces a look; a missing category shows a placeholder, not a failure.
- **Save and "Wear it today" both work** — Save proves "the creation loop is complete and the data is stored"; Wear it today adds the day to the calendar (learner decision during the build; streaks stay out).
- **The outfit card mirrors the item card** — instead of name chips under the look (design page 17), rows for the clothes, colors, category, seasons and delete (learner request).
- **Gallery upload only** for the demo.
- **Deferred features are left out, not shown disabled** — the Collections tab and OutfitCHECK were removed (learner decisions), and Friends gave its place to the Stylist chat; only the outfit card's Share button still says "Coming soon".
- **Japandi look** — warm neutrals, white canvas, serif headers, clothes as the accent.

## What We're Building
1. Splash, Welcome, and real Create Account / Log In (email + password) backed by a database, with a persistent session and Log out.
2. Database storing users, clothing items, outfits and the calendar, each tied to its owner.
3. Wardrobe main screen with Clothes and Outfits tabs, including the illustrated empty state, search and filters.
4. Add clothes from gallery with real AI background removal and a processing → View item flow.
5. Item card with the AI-filled type and colors (editable) and optional name, tags, seasons; photo editing; remove.
6. Create outfit canvas: random starting outfit, per-piece swiping, layers (under layer, outerwear, bag, accessories), missing-category placeholders, a filter, and the weather with a cold/heat tip.
7. AI stylist on the canvas: one-click generation with an optional request and follow-ups, only from owned items, with the unmatched-request fallback.
8. Outfit card after Next → Save → Outfits tab, persisting across sessions; a saved outfit can be labeled, edited and deleted.
9. Wear it today → an outfit calendar (one outfit per day).
10. Stylist chat: outfits for an occasion, pieces, the user's style and how to improve it.
11. Profile with photo, username, city and weather page; change password; delete account.
12. Japandi styling across all of the above.

## Deferred From the POC
- **Password reset, email verification, Google/Apple sign-in** — nice to have; email + password is enough to prove real accounts.
- **Camera capture** — gallery upload is enough for the demo.
- **Streaks, weather and OutfitCHECK photos in the calendar** — the calendar shows only the outfits worn.
- **Collections** (Work, Casual, Date night) on saved outfits — saving to the Outfits tab proves the loop. The Home tab for them was removed from the PoC (learner decision); they are planned for later.
- **Friends, sharing, ratings, the monthly top** — the social layer; the next step after the PoC (see Possible Later Enhancements). OutfitCHECK photos are later too.
- **AI auto-filling Tags, Season, and Name** — manual for now.

## Possible Later Enhancements
- **Collections**: a Home tab that groups saved outfits into themed collections (Work, Casual, Date night).
- **Stylist chat, the full version**: saved chat history with a list of past chats to go back to, and trying outfits on the user's own photo ("show how this outfit looks on me", with an image model). Sized on 2026-10-20 at about 2 more days, the try-on with the risk that the free Gemini key can't generate images at all.
- **OutfitCHECK** (design pages 21–22): + → OutfitCHECK opens a camera with a self-timer (Off / 3s / 10s, "Prop up your phone and step back"); the photo of the user in their outfit gets its background removed like a clothing photo and is kept for that day of the calendar, which shows it with a camera badge (design page 18). Sized on 2026-10-20 at about 1.5 days with our own camera as on page 21, or about 1 day with the phone's camera app; posting it to friends needs Friends first.
- **Friends — the next step after the PoC** (learner decision; shown as such in the demo). Private and friends-only, never a public feed:
  - **Add friends:** by their exact email, as a request the friend accepts or declines; no directory of users.
  - **Share outfits:** send a saved outfit to one or more friends from the outfit card's Share button; what friends sent is on the Friends tab, opened read-only.
  - **Rate outfits:** friends give each shared outfit a score.
  - **The month's best:** at the end of every month, the **top 3 outfits** with the highest scores, and the friend who collected the most points — **the most stylish of the month**.
  - Sizing: adding friends and sending outfits was estimated on 2026-10-20 at about 1–1.5 days (two tables — friendships, shared outfits — about eight endpoints with tests for the rules: not yourself, only accepted friends, only your own outfits; the Friends tab and a share sheet; no push notifications). Ratings and the monthly top are not sized yet.
- Everything else in `scope.md > Later`: streaks and the morning reminder, collections, Group Vibes, Wardrobe Swap, Travel Mode, Cost-per-Wear, Avatar Mannequin.

## Non-Goals
- **No public feed or public profiles** — the social vision is private and friends-only (the next step after this PoC).
- **No "one month later" time-skip demo** — cut in scope; it doesn't prove the styling engine.
- **No shopping or recommending new clothes** — Mixyn works only with what you own (mindful consumption).
- **No production deployment requirement** — deployment is optional for the hackathon.

## Open Questions
Resolved during the build:
- **Does the AI stylist include Outerwear in generated looks?** Yes, when it suits the look; it may also add a piece under the top, a bag and accessories.
- **Home tab vs Wardrobe:** "Home" in the nav is the wardrobe main screen.
