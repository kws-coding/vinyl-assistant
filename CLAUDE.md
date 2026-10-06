# Vinyl listing assistant: project brief

Personal tool for one user (a Java developer with ten years of experience) to list used vinyl records on eBay and Discogs faster. Not a service for others. Records first; the design must allow other categories (for example guitars) later without rewrites.

## Principles

- AI handles ambiguity and language: reading photos, extracting text, drafting titles and descriptions.
- Code handles correctness: confidence scoring, comp selection, price and profit math, API calls, state, event log.
- The human confirms consequential decisions: condition grades, ambiguous pressings, conflicting sales, and publishing.
- The AI never sets the condition grade. The user does.
- No invented facts. Every extracted value keeps the raw text it came from. "Not visible" is an allowed answer.
- Confidence is computed in code from per-identifier evidence (match, mismatch, missing). Never ask the model for a percentage.
- Escalate by value at risk: the price gap between plausible candidates, weighted by match.

## Current phase: proof of concept script

Goal: find out whether the tool can identify the exact Discogs release from photos and price it, in fewer human minutes than doing it by hand.

Rules for the POC:
- Java 21, plain Maven or Gradle, no Spring, no database, no UI, no abstractions beyond what is below.
- Keep domain logic in plain classes with no framework imports so it can be copied into the real app: scoring, evidence model, value at risk, profit math. Write unit tests for these.
- Discogs client, request/response DTOs and mappers are separate from domain classes.
- The script's runner, CSV input and report writer are throwaway.
- Ask before adding a dependency or framework. Verify current library coordinates and API shapes from official docs before using them.

Secrets: `VINYL_ANTHROPIC_KEY` and `DISCOGS_TOKEN` come from environment variables. Never print them, log them, or commit them.

### Input

```
records/
  001/<any filenames: IMG_4021.jpg, IMG_4022.jpg, ...>
  002/...
records.csv   id,bucket,media_grade,sleeve_grade,known_defects,cost_basis,truth_release_id,ebay_sold_avg,notes
```

`known_defects` is the user's own free-text list of defects they know about (for example "seam split, light scuff side A, plays quiet"). It is used only to score the condition experiment below.

Do NOT rely on file names or photo order. In real use the user uploads unnamed photos in a batch from the desktop, and the tool must work out what each photo shows. One folder is one record. (Optional for scoring: a `photo_roles.csv` with `record_id,filename,role`, where role is front, back, labels, barcode, runout_a, runout_b, other.)

`bucket` is one of: easy, ambiguous, price_sensitive, runout_dependent, edge. `truth_release_id` is the Discogs release the user has confirmed by hand (the answer key). `ebay_sold_avg` is optional, looked up manually in eBay Seller Hub Research.

### Per record

1. Resize photos to cap cost.
2. Send all of the record's photos to the vision model and get back, per photo, its role (front, back, labels, barcode, runout_a, runout_b, other) plus structured observations only (catalog number, label, country, barcode digits, format, matrix text), each with raw text. Start with one call per record. Also try a two-step variant (cheap role classification, then extraction per role) and compare cost and accuracy. Photos with an unclear role are reported, not guessed.
3. Search Discogs by barcode, then catalog number plus label, then artist and title; fetch the top candidate releases.
4. Score each candidate in code, identifier by identifier. Compute value at risk from the price gap between candidates.
5. Decide: confident, needs the user's call, or needs runout photos.
6. Price the chosen release from Discogs data, filtered to the user's condition grade if the API allows. Compute a profit estimate.
7. Write one report row, and log every API call with its AI cost.

### Condition experiment (observations only)

Run after identification works, on a handful of records for which the user also took photos of each side of the vinyl surface under a lamp, plus sleeve photos.

- The model returns observed defects only, each with the photo it came from, the location and a confidence in words: sleeve (ring wear, seam splits, writing, corner dings, foxing, stains) and vinyl surface (visible scuffs, scratches, marks).
- It must never output a grade (M, NM, VG+ and so on) and never claim anything about how the record plays. Warps, surface noise and groove wear cannot be seen in photos. It states that limit in the report.
- It also drafts consistent condition-note wording from the observations, within Discogs' 500-character, no-HTML limit, using only observed facts and the user's own notes.
- Flag disagreements: where the observations suggest the user's grade may be too generous, say so, and let the user decide.
- Score it against the user's `known_defects` and grades: defects found, defects missed, false alarms, and how often a flag would have changed the user's grade.

### Measure

- Per bucket: correct, flagged (correct or incorrect), or wrong and unflagged. Wrong and unflagged on a high-value record is the failure that matters most.
- Photo role accuracy (when `photo_roles.csv` exists), and which missing roles (for example no runout photo) caused a record to be flagged.
- Human minutes per record (the user times themselves), AI cost per record, pricing error against `ebay_sold_avg`, and a failure taxonomy.
- Propose numeric pass/fail thresholds BEFORE the full run and get the user's approval first. Primary metric: human minutes from first photo to approved listing, subject to no unflagged high-value errors.

### First hour: hello-world calls

Run these on one record before building anything else, and report what each returned:
1. Discogs database search and release lookup with the personal access token.
2. Discogs marketplace stats for a release. Does the API return a sales history with per-sale condition? Does it return price suggestions by condition? (The website shows both a sales history that lists condition and a single suggested price that does not change with condition.)
3. Whether image URLs from the release endpoint load with the token alone.
4. One vision call on one record's photos (unnamed, in any order), returning each photo's role and the structured observations record. The vision model name is a config setting.

## Constraints learned in Phase 0

Discogs:
- Authenticated limit is 60 requests per minute; throttle and read the rate-limit headers.
- API Terms of Use: show "Data provided by Discogs" with a link; do not cache or store Discogs content longer than needed (store release IDs and our own fields, refetch the rest); marketplace data is non-commercial use only.
- Listing form has no photos. Fields: condition, comments (500 characters, no HTML), private item location, quantity, price, weight, counts-as. Selling fee 9%.

eBay:
- Buy APIs (Browse, Marketplace Insights) are restricted to approved partners. Do not use eBay for comps in code. eBay sold prices are looked up manually in Seller Hub Research (up to 3 years back, no known API).
- Sell side (Inventory API) is the later publishing path. Production keys need account-deletion notifications or an exemption; store no buyer data.
- Fee model (placeholder, unverified): 13.6% of (item + shipping charged + sales tax) plus $0.40 per order. Keep as editable settings.

Seller setup already done on eBay: "Single LP" shipping policy (USPS Media Mail, flat $5.50, 1-day handling), no-returns policy, immediate-payment policy. Real postage for a single LP and supplies cost (about $1 mailer) are settings, not per-item questions.

Profit estimate:
```
fee    = rate * (item + shipping_charged + tax) + 0.40
profit = item + shipping_charged - fee - postage - supplies - cost_basis
```
Shown as an estimate; actual label cost is recorded after the sale.

## The real app (do not build yet)

- Spring Boot, Java 21, PostgreSQL in Docker with JSONB for per-category attributes, local file storage for photos, Thymeleaf + HTMX, desktop-first, runs on localhost, single user, no sign-up.
- Item category is data: a CategoryProfile holds the attribute schema, photo slots, condition scale, identification strategy, comp rules, listing template and shipping profile. Only records are implemented in v1.
- Five pipeline modules as services: identification, pricing and profit, listing drafts, publishing, sale sync. Modules do not call each other in a chain; each reads the item, writes its result and advances the item state.
- Naming: request and response DTOs at the web layer, separate persistence entities, commands only where a non-HTTP caller (sync job, background identification) needs one.
- Item states: DRAFT, IDENTIFIED, READY, LISTED, SOLD_PENDING_DELIST, SALE_CONFLICT, SOLD, CANCELLED.
- Every step, decision and AI call is recorded in an event log with its cost.

## Working agreements

- Small steps. Show the plan before big changes.
- Tests for domain logic.
- Say plainly when something cannot be verified. Do not guess API behavior; test it.
