# Open questions and unverified items

Things that are guessed, untested, or not confirmed from official docs. Remove an item when it is settled.

## Decisions waiting on the user
- Numeric pass/fail thresholds for the full run: drafted in `THRESHOLDS.md` (2026-10-05). User set the $25 high-value cutoff and the 10-minute target. Final approval of the rest is still needed before the run.
- Escalation limit (`Thresholds.riskLimit`, now a flat $5) vs the $25 test cutoff. Handle later, after Wednesday's run: replay the recorded gaps at several limits and pick from data. Consider a percentage-based rule (for example the larger of $5 or 15% of price), since a flat $5 means very different things at $10 and at $100.
- eBay handling is a stopgap and needs a better design later (user flagged 2026-10-05). eBay figures are typed in by hand from Seller Hub, and many eBay listings do not show runouts, so a sale may be a different pressing and is a weak yardstick. Built so far: optional `ebay_sold_high` and `ebay_match` (same, unsure, no) columns, and an alert column that fires when the high sale is at least 2x the Discogs price and at least $20 above it (both numbers are guesses; `EbayAlert.defaults()`). It never changes a decision or a price, and a sale marked `no` raises nothing. Open: how bar 6 should weight eBay by `ebay_match` (not decided); whether to keep one average and one high or a list of sales; whether the alert should also fire when eBay is far below the Discogs price; and how to get eBay data in without typing it (no comps API is available to us).
- Report columns for `discogs_touches` and `annoyance` (bar 8) are not added yet.
- Real single-LP postage cost. For the POC the runner uses a placeholder of 5.50 unless `POC_POSTAGE` is set, and the report says so. User says the real label cost is usually a bit less and varies with weight, so profit leans slightly low. Tests use a made-up 4.50.
- Settled 2026-10-05: the AI may suggest a grade, blind to the user's grade; the user overrides. CLAUDE.md was edited. Open: how good the suggestions are (untested), and the pass bar for the condition experiment (none proposed yet).

## Guessed values (tune from the full run)
- `ScoringWeights.defaults()`: barcode 3, matrix 3, catalog number 2, country 1, label 1, format 0.5.
- `Thresholds.defaults()`: confident 0.7, plausible rival 0.4, risk limit $5.
- A mismatch subtracts its weight and cancels a match. Barcode or matrix mismatch may need to disqualify outright.
- Value at risk uses the largest weighted gap, not the sum across rivals.
- Two close pressings with a small price gap come out confident. A lower risk limit may be needed.
- Settled 2026-10-05 after the test audit: `Decider` now asks for runout photos only when a barcode or catalog number matched, nothing mismatched, and only the matrix is missing; otherwise it goes to the user and the reason says what to photograph. A missing price is its own report column (`price_missing`), not a decision change. A truncated barcode read (a piece of a listed barcode, 7+ digits) now counts as missing evidence instead of a mismatch.
- Still open: a full-length barcode with one wrong digit is still a mismatch (score drop 0.86 to 0.29 on the replay). Decide after real photos show how often barcodes are misread.
- Still open: a single candidate with no price comes out CONFIDENT by design (the id is confident; price is reported separately). Price reliability is unchecked until `ebay_sold_avg` exists.

## Ideas (not planned, user raised 2026-10-05)
- Identification as a small paid service: someone digging through records wants the right Discogs release fast, and Discogs navigation is slow. The user's framing: first a private tool that helps them, a way to learn Claude Code, and possibly a product; Discogs is an old site and might want to help with something useful.
- Check before any effort: the Discogs API terms of use (marketplace data is non-commercial only; do not store Discogs content). Whether charging for a service built on their data is allowed has NOT been checked. Also unchecked: what Discogs' own app already does.
- The POC does not test this use case. In a store there are one or two photos of a cover or barcode on a phone, not up to 11 photos with runouts, so accuracy and cost per lookup would differ. Only one record is measured so far.
- AI cost per lookup would be small, but usage caps matter (see the cost-control note below). Accounts, billing and support are a different project from a personal tool.
- Decide after the 10-record test shows whether identification works at all.

## Later (real app)
- Cost control for uploaded photos that never become listings: vision calls should run only on an explicit user action, with a per-day or per-batch budget, and every call logged with its cost in the event log. The POC only has a photo cap per record (`POC_MAX_PHOTOS`, default 11) and the vision cache.

## Unverified facts
- eBay fee model (13.6% of item + shipping + tax, plus $0.40) is a placeholder from the brief. Tax rate defaults to 0.
- Discogs fee model is 9% of (item + shipping + tax), no flat fee (`ProfitSettings.discogs`). Only the 9% comes from the brief. The user says the fee applies to both the item price and the shipping charged (user statement, 2026-10-05; not checked against Discogs docs), which matches the formula. Unverified: whether payment processing fees come on top. User says Discogs matters more than eBay for the POC; the report shows profit for both (`profit_discogs_usd`, `profit_ebay_usd`), at the same Discogs suggested price and the same $5.50 shipping charged.
- JUnit 6.1.3 and Jackson 3.2.3 coordinates came from Maven Central metadata. The JUnit docs pages did not show them. Jackson 3 notes were read from the official wiki. Both build and run.
- No Discogs sales-history endpoint found. Only `/marketplace/sales/{id}`, `/releases/{id}/sales`, `/marketplace/history/{id}` (all 404) and `/releases/{id}/stats` (returns only `is_offensive`) were tried.
- Price suggestions return a price per grade. Only checked on two releases. Whether they depend on the seller account is unknown.
- Search by catalog number plus label works, but it is not known whether it finds the right release for record 001.
- Replay of record 001 with evidence stripped (2026-10-05, cached vision result, Discogs calls only): the artist-and-title fallback fetches only the top 15 results and the true release was not among them, so with no barcode and no catalog number the right release is never scored. It was flagged, but only because every score was 0. No variant produced a wrong-and-unflagged result. One record and synthetic degradations, so this says little about real accuracy.
- Search returns only the first page (50 results). Barcode search for record 001 returned 10. Pagination is not handled.
- 429 retry and error handling were tested only against a fake local server, not live.
- Discogs terms: show "Data provided by Discogs" with a link in reports, do not store Discogs content, non-commercial marketplace data only. Not yet enforced in code.

## Vision
- Accuracy is unmeasured beyond record 001. Runout reads there were wrong or partial every time: shell run read side B as `NC60016-01` (actual `BK06016-01`); the Java client read side A as `00812227` (actual `0081227`, one extra digit) and side B as `6016-01` (correct but partial). The evidence builder must not demand exact equality on matrix text.
- Photo role accuracy is unmeasured (no `photo_roles.csv` yet). On record 001 all 11 roles looked right to a human check of the 2-3 photos I viewed, not all 11.
- Cost per call on record 001 (n=1 each): shell version about $0.079 (2,675 output tokens); Java client at effort `medium` with no per-photo notes about $0.063 (946 output tokens). The saving is not established: one sample each, and quality at `medium` is untested. `VISION_EFFORT` default `medium` is a guess.
- Token prices (`VISION_PRICE_IN/OUT`, 2.00 and 10.00 per million) come from a cached table dated 2026-09-25, not a live lookup.
- Structured output (`output_config.format` json_schema) plus `effort` worked live on the first try with `claude-sonnet-5-5`. Behavior with other models (for example Haiku 4.5) is untested.
- Two-step variant (cheap role classification, then extraction per role) is not tried.
- HEIC resizing (`ImageResizer`) shells out to macOS `sips`. Throwaway and macOS-only.

## Evidence rules (`EvidenceBuilder`): assumptions to check on real data
- Tested only on record 001 and made-up releases. Nothing here is measured on other records.
- Country: Discogs "Worldwide" and "Europe" count as missing, not a mismatch, because a German label fits both. A specific different country (for example "US") is a mismatch, even though imports and exports exist.
- Matrix: a fragment of 5+ characters matches if it appears in a listed runout, with one misread character allowed from 6 characters. No fit against a non-empty list is a MISMATCH. Discogs lists are often incomplete, so this can wrongly penalise the right release. The safe direction is that a mismatch on the top candidate sends the record to a person, but it can cost confident calls.
- Barcode: digits only, leading zeros ignored; candidate "barcode" entries that are not purely digits are skipped (Discogs mixes in text like "ASCAP" and matrix strings). Reads under 7 digits are ignored.
- Label: matches when either name contains the other, after dropping punctuation. May be too loose for short names.
- Format: only stereo and mono are compared. "180g" and similar are ignored.
- Artist, title and year from the photos are not used for evidence yet (only for the later artist and title search).
- The pipeline test for record 001 uses made-up releases shaped like the real ones, because Discogs terms say not to store Discogs content.

## Measuring (built 2026-10-05, throwaway)
- `SummaryMain` reads a report CSV and prints per-bucket results, wrong-and-unflagged records with their price gap (`error_gap_usd`), cost, pricing error by `ebay_match`, human effort and photo role accuracy, with the DRAFT bars from `THRESHOLDS.md` hard-coded in `Summary` (keep them in step; they are not approved). Record 001 is excluded by default.
- Photo role scoring maps `photo_1..N` to the sorted file names in `records/<id>/`, so it breaks if photos are added or removed after the vision call. A count mismatch is reported and the record is skipped. Only tried with a role file built from the model's own answers (a plumbing check, 11 of 11 by construction); real role accuracy is unmeasured.
- `error_gap_usd` is blank when either price is missing, and the summary then says the gap is unknown and asks for a check on bar 1, not a pass.
- Failed records (for example over the photo cap) are printed by the runner but are not rows in the report, so the summary cannot count them.
- Bar 3 is read as the share of records that are not wrong-and-unflagged, per bucket. If you meant something stricter, say so.

## Condition experiment (built 2026-10-05, never run on real photos)
- `ConditionMain` reads photos from `records/NNN/condition/` (vinyl surface under a lamp, sleeve; up to `POC_MAX_CONDITION_PHOTOS`, default 8). The identification step ignores that subfolder. `ConditionClient.assess` takes photos only, so the user's grade, notes and known defects cannot reach the model. It suggests a visual-only media grade and sleeve grade (or cannot_tell) with the defects behind each; the user's grade is always the one used.
- Lock grades in `records.csv` (and commit) before running, so the model and the user do not influence each other.
- Outputs: `reports/condition-*.csv` (suggested vs your grade, direction and steps, flag, `user_decision` to fill in as keep or change) and `reports/condition-review-*.csv` (mark each observed defect real, false_alarm or unsure, and each known defect found or missed). `ConditionMain --summary` counts agreement, direction, false alarms, misses and how often a flag would change your grade.
- Untested on real photos. The prompt, the schema and the grade scale wording are first drafts; the model's own idea of the Discogs scale is unverified. Cost per record is an extrapolation (about $0.03 to $0.06), not measured.
- Draft bars C1 to C7 are in `THRESHOLDS.md` and in `ConditionSummary` (not approved; keep the constants in step).
- The Discogs comment is drafted by `ConditionMain --notes` from a template in `ConditionNote`, not by the model: reviewed defects, "none seen in photos" only when both sides were photographed, and the user's own `listing_notes` column. It enforces 500 characters and no HTML. A model-polished version is not built; the template is plainer but consistent and cannot invent anything.
- Each run of `ConditionMain` writes a NEW blank review sheet, so the one you marked is not the newest. Pass `--review FILE` to `--notes` and `--summary`. Easy to get wrong; consider overwriting only blank sheets.
- A known defect that cannot be seen in a photo (for example "plays quiet") should be marked `not_visible` in the review sheet so it does not count as a miss.
- The flag rule is simply "suggested grade differs from yours", in either direction, by any number of steps. Whether one step apart should count as a flag is undecided.

## Not built yet
- Failure taxonomy (categories of failure); worth doing once there is real data.
- Two-step vision variant (cheap role classification, then extraction per role).
