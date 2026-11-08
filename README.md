# What Should I Eat Today

An Android app that keeps track of what's actually in your kitchen, and hands
that list to ChatGPT or Claude to answer one question: **what should I eat
today?**

Not a week of meal planning. Not batch cooking. Today.

## How things get into the kitchen

**Scan them.** A continuous camera session — point at a barcode, it beeps and
saves, and the camera stays open so a whole cupboard can be worked through in
one go. Barcodes are looked up against [Open Food Facts](https://world.openfoodfacts.org/)
for a real product name and category. When a barcode isn't known, a prompt
appears over the live preview so naming it by hand doesn't break the flow.

**Import a Lidl shop.** Sign in to Lidl in a WebView and the app pulls your
receipt history straight from Lidl's own API, then lets you pick which shops to
import. A receipt from two months ago is shopping history, not food you still
have, so the choice is explicit rather than automatic.

**Type it in.** Loose produce has no barcode.

## Why there are no expiry dates

There were, once. The idea was to estimate a use-by date from the purchase date
and a shelf-life table, and flag what was about to go off.

It didn't survive contact with real data. Estimating a date for *everything*
means guessing for the many products the classifier doesn't recognise, and a
jar of Nutella confidently "expiring" in seven days is worse than no date at
all. The feature was removed; the Kitchen is now an inventory of what you have.

The database columns are still there, unused, so the idea can come back for the
narrow case where it genuinely works — fresh produce — without a migration.

## Classifying products

Supermarket descriptions are hostile: truncated (`Low Fat Cottage Ch.`), run
together (`6FrozenSalmonFillets`), accented (`Baresa Tomato Purée`), and full of
article codes (`Vine Tomatoes 0082465`).

Two things do the work:

1. **Open Food Facts category tags**, when a barcode was scanned. These carry
   the *preparation*, which the product name doesn't: a tin of chopped tomatoes
   is tagged `en:canned-tomatoes` as well as `en:tomatoes`. Preparation wins.
2. **A keyword table**, as the fallback and for receipt imports. Whole-word
   matching only, ordered most-specific-first — otherwise "Peppermint Tea"
   matches `pepper` and becomes a salad vegetable.

One finding from real Lidl receipts is worth recording: **loose fresh produce
has its 7-digit article code appended to the description**, and packaged goods
never do. Across a sample of 445 line items this held in 141 of 141 cases, so
it reliably identifies fresh produce even when the name means nothing.

## Layout

```
app/src/main/java/com/example/whatcanicook/
  domain/     product classification, categories, the kitchen item model
  data/       SQLite persistence (framework API, no ORM)
  lidl/       receipt API client, HTML receipt parser, WebView login
  scan/       continuous barcode scanner, Open Food Facts lookup
  ai/         prompt builder and the ChatGPT / Claude hand-off
  ui/         Compose screens
tools/lidl/   desktop scripts used to explore the Lidl API (see its README)
```

Kotlin + Jetpack Compose, minSdk 26. Persistence is the framework SQLite API
rather than Room, to avoid depending on an annotation processor that matches
this toolchain. The schema is Room-shaped so it can be swapped later.

`onUpgrade` deliberately **never drops tables**. Items scanned in by hand can't
be re-fetched from anywhere, so any future schema change must be an additive
`ALTER TABLE`.

## Running it

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Needs `local.properties` pointing at an Android SDK. Not committed.

## Privacy

Receipt data, kitchen databases and Lidl session cookies stay on the device and
are gitignored. The Lidl login happens in a WebView — the app never sees the
password, only the resulting session cookies, which live in app-private storage.

Unofficial and not affiliated with Lidl.
