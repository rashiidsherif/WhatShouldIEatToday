# LIDL import — findings from the spike

Status: **working**. Pulled 36 real receipts / 445 line items from a live account.

## The two APIs

| | Web receipt API | Lidl Plus mobile API |
|---|---|---|
| Base | `www.lidl.co.uk/mre/api/v1` | `tickets.lidlplus.com/api/v2` |
| Auth | first-party cookies on `www.lidl.co.uk` | OAuth2 + PKCE bearer, with refresh token |
| Items | HTML receipt, must be scraped | structured JSON |
| Status here | **proven, in use** | client written, token not yet obtained |

### Web API (what currently works)

```
GET /mre/api/v1/tickets?country=GB&page=1
GET /mre/api/v1/tickets/{id}?country=GB&languageCode=en-GB
```

- List response: `items[]` with `id`, `date`, `totalAmount`, `articlesCount`, `store`, `isHtml`, plus `totalCount` / `size` (page size 10).
- Detail response nests everything under `ticket`; **`ticket.totalAmount` is always `0`** — take the real total from the list response or the receipt HTML.
- Receipt body is `ticket.htmlPrintedReceipt`. Articles are `span.article` carrying
  `data-art-id`, `data-art-description`, `data-unit-price`, `data-art-quantity`, `data-tax-type`.
- Discounts are `span.discount` groups — group them by the `purchase_list_line_N` id,
  **not** by `data-promotion-id`, which repeats across every qualifying line.

### Mobile API (the target for the app)

`client_id=LidlPlusNativeClient`, redirect `com.lidlplus.app://callback`,
token endpoint `accounts.lidl.com/connect/token` with Basic `LidlPlusNativeClient:secret`.
Device-code grant is advertised in the discovery document but returns
`unauthorized_client` for this client, so it has to be authorization-code + PKCE.

On a laptop the blocker is capturing the custom-scheme redirect. **In the Android app this
problem disappears** — the app registers the scheme and receives the callback natively, so
this is the right path for WhatCanICook. One login, then a refresh token for silent re-sync.

## Environment gotchas

- The Bash tool runs behind a TLS-intercepting sandbox proxy; Python there fails cert
  validation against `accounts.lidl.com`. **Run these scripts via PowerShell.**
- Login is defended by reCAPTCHA Enterprise v3, FingerprintJS *and* Akamai Bot Manager.
  Scripted credential login is not viable — a real browser login is the only route.

## Data findings that shape the Kitchen model

Receipts carry **no expiry information at all** — only a product description and a purchase
date. So every date in the Kitchen is either scanned by the user or estimated.

**Loose fresh produce is reliably identifiable.** Those items have their 7-digit article
code appended to the description (`Vine Tomatoes 0082465`), and that trailing code equals
`artId` in **141 of 141** cases. Packaged goods never have it. That single rule separates
"needs an estimated use-by" from "probably has a printed date", and it covers 87 distinct
produce items here — a shelf-life table of that size is very tractable.

Pitfalls found while testing a naive matcher:
- Substring matching misfires: `pepper` matched **Peppermint Tea**. Match whole words, and
  check produce-code items first.
- Non-food lines need filtering: `Carrier Bag`, `Flexi Loop Carrier`, `Heavy Duty Bag`.
- Descriptions are truncated and abbreviated (`Low Fat Cottage Ch.`, `6FrozenSalmonFillets`,
  `Ripe & Ready Nectari`), so exact-name lookups against a product database will miss.
  Normalise and fuzzy-match.
- `data-art-quantity` is present on only 77 of 445 lines; absent means quantity 1.

## Files

- `lidl_plus.py` — mobile OAuth2/PKCE client (`authurl`, `exchange`, `refresh`, `tickets`, `ticket`, `dump`).
- `analyse.py` — shelf-life coverage report over a dump.
- `data/` — personal receipt data and tokens. **Gitignored.**

## Re-running the web pull

Log in at `https://www.lidl.co.uk/mla/` in a browser, then from that page's context call the
two endpoints above with `credentials: 'include'`. Cookies expire, so this needs a fresh
login periodically — another reason the app should use the mobile API.
