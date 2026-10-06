---
doc: scope
status: approved
---

# Mixyn

An AI stylist that builds outfits from the clothes you actually own.

## The Unique Kernel
Your real wardrobe, digitized in seconds, plus an AI stylist that builds a complete look **from those exact items** — in one click, or around a hint like "I want to wear this oversized shirt" or "build me an outfit around burgundy." A generic outfit generator doesn't know your closet or what you're in the mood for; this one does.

## Who It's For
The learner first: someone who cares about fashion and a versatile, mindfully built wardrobe. Most mornings are easy — they start from the pants and build intuitively. But on bad-mood mornings "nothing feels right," choosing becomes frustrating, and today they end up asking their boyfriend to decide.

Beyond the learner: anyone who owns more clothes than they actually wear — people who want to shop less and style what they already have, and who lose time and mood every morning to "I have nothing to wear" while standing in front of a full closet. Mixyn turns the wardrobe they already paid for into ready outfits.

## The Core Loop
Open the app → go to the Carousel → a random outfit from your wardrobe is already loaded. If it clicks, done. If not, swipe tops / bottoms / shoes to mix and match. Still stuck? Hit **Generate with AI**, optionally with a hint, and let the AI do the thinking. You come back because it turns a stuck morning into a quick decision using clothes you already have.

## Inspiration & Identity
Digital wardrobe breakdowns and outfit showcases on Instagram. Values: mindful consumption — every piece pairs with others, rather than buying more. Social vision is private and friends-only, "without the pressure or noise of becoming a public content creator on TikTok" (post-PoC).

## Why This Matters to the Learner
They want a tool for their own daily wardrobe — "getting something working is extremely important to me" — and to solidify backend skills: "structuring robust backend architectures, managing data, and seamlessly integrating AI features." Upload processing and AI generation are exactly that work.

## What "Working" Looks Like
A ~1-minute demo:
1. Sign up with a real account — the wardrobe is stored in a database and is still there after logging out and back in.
2. Upload a few real clothing photos — the background is removed automatically and each becomes a clean digital item.
3. In the Carousel, swipe through tops, bottoms, and shoes to build an outfit by hand.
4. **The "aha" beat:** hit **Generate with AI** (optionally typing a hint) and a complete look appears, built only from the uploaded items.

## The POC Boundary
- **Real accounts from the start:** sign-up and log-in (email + password), with the user's profile, clothes, and outfits stored in a real database
- Upload clothes with AI background removal
- Outfit Carousel: mix and match tops, bottoms, shoes (starts with a random combination)
- AI outfit generation from the user's own items: one click, plus an optional hint field
- Current weather for the user's city, shown on the outfit screen and taken into account by the AI stylist (added during the build at the learner's request)

## Later
- The cold-weather layering step (jacket / hat / scarf / gloves suggestions) and a weather forecast for planning ahead
- AI auto-tagging of material and season (category and color are in the PoC — see `prd.md`)
- Accessories step, themed collections ("office," "date")
- Social: friends, sharing, OutfitCHECK photos, star ratings, monthly leaderboard
- Outfit calendar and daily streak
- Pitch extras: Group Vibes, Wardrobe Swap, Travel Mode, Cost-per-Wear analytics, Avatar Mannequin

## Explicitly Cut
- **"One month later" time-skip demo** — depends on the calendar, social layer, and seeded history; none of it proves the styling engine.
- **Social in the PoC** — great for retention, but it "can be built later once the core styling engine works." (Weather was cut here too at first; a minimal version came back during the build.)
