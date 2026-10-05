# Open questions and unverified items

Things that are guessed, untested, or not confirmed from official docs. Remove an item when it is settled.

## Decisions waiting on the user
- Numeric pass/fail thresholds for the full run (propose first, user approves before the run).
- Real single-LP postage cost (no default in `ProfitSettings`; tests use a made-up 4.50).
- Whether the AI may suggest a condition grade. CLAUDE.md currently says the AI never sets or outputs a grade. User was open to "suggest, user confirms or overrides" as a future feature; CLAUDE.md would need a deliberate edit.

## Guessed values (tune from the full run)
- `ScoringWeights.defaults()`: barcode 3, matrix 3, catalog number 2, country 1, label 1, format 0.5.
- `Thresholds.defaults()`: confident 0.7, plausible rival 0.4, risk limit $5.
- A mismatch subtracts its weight and cancels a match. Barcode or matrix mismatch may need to disqualify outright.
- Value at risk uses the largest weighted gap, not the sum across rivals.
- Two close pressings with a small price gap come out confident. A lower risk limit may be needed.

## Unverified facts
- eBay fee model (13.6% of item + shipping + tax, plus $0.40) is a placeholder from the brief. Tax rate defaults to 0.
- JUnit 6.1.3 and Jackson 3.2.3 coordinates came from Maven Central metadata. The JUnit docs pages did not show them. Jackson 3 notes were read from the official wiki. Both build and run.
- No Discogs sales-history endpoint found. Only `/marketplace/sales/{id}`, `/releases/{id}/sales`, `/marketplace/history/{id}` (all 404) and `/releases/{id}/stats` (returns only `is_offensive`) were tried.
- Price suggestions return a price per grade. Only checked on two releases. Whether they depend on the seller account is unknown.
- Search by catalog number plus label works, but it is not known whether it finds the right release for record 001.
- Search returns only the first page (50 results). Barcode search for record 001 returned 10. Pagination is not handled.
- 429 retry and error handling were tested only against a fake local server, not live.
- Discogs terms: show "Data provided by Discogs" with a link in reports, do not store Discogs content, non-commercial marketplace data only. Not yet enforced in code.

## Vision
- Accuracy is unmeasured. On record 001 the model read side B runout as `NC60016-01`; it is `BK06016-01`. Side A runout is only partly legible.
- Photo role accuracy is unmeasured (no `photo_roles.csv` yet).
- The first call hit `max_tokens` (2000) because thinking used it up; set to 8000. Cost in dollars is not computed because model pricing has not been verified.
- Two-step variant (cheap role classification, then extraction per role) is not tried.
- HEIC resizing uses macOS `sips` in a shell script. Throwaway and macOS-only.

## Not built yet
- Evidence builder (model observations + `ReleaseInfo` into `IdentifierEvidence`, including how to compare partial matrix text and barcode formatting).
- Vision client, runner, CSV input, report writer, call log with AI cost.
- Condition experiment.
