---
doc: scope
status: approved
---

# Mixyn

An app for the clothes you already own: put outfits together from your own wardrobe, with an AI stylist
to help when you don't know what to wear.

## The Idea
In the learner's words (edited for language, 2026-10-22):

> I love clothes, and I wanted to build an app around them. I found a problem that almost everyone runs
> into, again and again: you don't know what to wear. So I made an app where you upload your own clothes
> and put outfits together from them. If you can't get a look right on your own, an assistant is right
> there in the outfit screen and suggests how to make it better. And there is a stylist agent that helps
> with your style as a whole: it tells you how to improve it, puts together several outfit ideas when you
> don't know what to wear, and much more.

## The Unique Kernel
Your real wardrobe, digitized in seconds, plus an AI stylist that builds a complete look **from those exact items** — in one click on the outfit screen, or around a request like "I want to wear this oversized shirt", "build me an outfit around burgundy" or "dinner with friends in the cold". A generic outfit generator doesn't know your closet or what you're in the mood for; this one does. For bigger questions, a stylist chat knows the same closet: several outfits for an occasion, what you haven't worn for a while, what your style is and how to make it better — always with what you own, never by buying more.

## Who It's For
The learner first: someone who cares about fashion and a versatile, mindfully built wardrobe. Most mornings are easy — they start from the pants and build intuitively. But on bad-mood mornings "nothing feels right," choosing becomes frustrating, and today they end up asking their boyfriend to decide.

Beyond the learner: anyone who owns more clothes than they actually wear — people who want to shop less and style what they already have, and who lose time and mood every morning to "I have nothing to wear" while standing in front of a full closet. Mixyn turns the wardrobe they already paid for into ready outfits.

## The Core Loop
Open the app → **+ → Create outfit** → a random outfit from your wardrobe is already on the canvas. If it clicks, done. If not, swipe the top, the bottoms or the shoes to change just that piece, and add layers: something under the top, outerwear, a bag, accessories. Still stuck? Tap **Ask AI** on the same screen, optionally with a request, and let the AI do the thinking. Then **Next → Save** or **Wear it today**, and the look is in your Outfits and your Calendar. You come back because it turns a stuck morning into a quick decision using clothes you already have.

## Inspiration & Identity
Digital wardrobe breakdowns and outfit showcases on Instagram. Values: mindful consumption — every piece pairs with others, rather than buying more. Social vision is private and friends-only, "without the pressure or noise of becoming a public content creator on TikTok" — the next step after the PoC (see Next Step).

## Why This Matters to the Learner
They want a tool for their own daily wardrobe — "getting something working is extremely important to me" — and to solidify backend skills: "structuring robust backend architectures, managing data, and seamlessly integrating AI features." Upload processing and AI generation are exactly that work.

## What "Working" Looks Like
A short demo:
1. Sign up with a real account — the wardrobe is stored in a database and is still there after logging out and back in.
2. Upload a few real clothing photos — the background is removed automatically, and the AI names each piece (its type and colors).
3. Create outfit: a random look is already on the canvas; swipe tops, bottoms and shoes to build an outfit by hand, and add a layer.
4. **The "aha" beat:** tap **Ask AI** (optionally typing a request) and a complete look appears, built only from the uploaded items.
5. Save it, or **Wear it today**, and find it in the Outfits tab and the Calendar.
6. Ask the Stylist chat for "3 outfits for a date" or "how can I improve my style?".

## The POC Boundary
As built (the build grew in agreed steps; each change is recorded in `checklist.md > Revisions`):
- **Real accounts from the start:** sign-up and log-in (email + password), with the user's profile, clothes and outfits stored in a real database; a profile with a photo, a username and a city, a new password, and deleting the account
- Upload clothes with AI background removal; the AI picks the type (from a fixed list) and up to three colors, and turns away photos that aren't one piece
- An item card to correct and enrich each piece (type, colors, tags, seasons, name, photo size and rotation)
- Outfit canvas: mix and match tops, bottoms and shoes (starts with a random combination), plus layers — under layer, outerwear, bag, accessories
- AI outfit generation from the user's own items, right on the canvas: one click, plus an optional request, with follow-ups ("other shoes")
- A Stylist chat: outfits for an occasion, pieces from the closet, a description of the user's style and tips to improve it
- Saved outfits (a top, bottoms and shoes, or a dress and shoes), editing them, **Wear it today** and a calendar of what was worn
- Current weather for the user's city: on the canvas, in the stylist's choices, in a cold/heat tip, and on a weather page (added during the build at the learner's request)
- Search and filters for clothes and outfits

## Next Step: Friends
The first thing after the PoC (learner decision; left out of the PoC only for time, see `prd.md > Possible Later Enhancements`):
- **Add friends** — a private circle, never a public feed.
- **Share outfits** with friends and see the ones they share.
- **Rate friends' outfits.**
- **At the end of every month:** the **top 3 outfits** with the highest scores, and the friend who collected the most points — **the most stylish of the month**.

## Later
- The morning outfit reminder and a daily streak in the calendar
- **OutfitCHECK:** a photo of yourself in the day's outfit, kept in the calendar and shared with friends
- Themed collections of saved outfits ("office," "date")
- The full Stylist chat: saved chat history, and trying outfits on your own photo
- AI auto-tagging of material and season (type and colors are in the PoC)
- Planning outfits for the days ahead with the forecast (the weather page already shows it)
- Pitch extras: Group Vibes, Wardrobe Swap, Travel Mode, Cost-per-Wear analytics, Avatar Mannequin

## Explicitly Cut
- **"One month later" time-skip demo** — depends on the calendar history, the social layer and seeded data; none of it proves the styling engine.
- **Social in the PoC** — great for retention, but it "can be built later once the core styling engine works." It is the next step after the PoC (see Next Step). (Weather was cut here too at first; it came back during the build.)
