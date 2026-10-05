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
- Accuracy is unmeasured beyond record 001. Runout reads there were wrong or partial every time: shell run read side B as `NC60016-01` (actual `BK06016-01`); the Java client read side A as `00812227` (actual `0081227`, one extra digit) and side B as `6016-01` (correct but partial). The evidence builder must not demand exact equality on matrix text.
- Photo role accuracy is unmeasured (no `photo_roles.csv` yet). On record 001 all 11 roles looked right to a human check of the 2-3 photos I viewed, not all 11.
- Cost per call on record 001 (n=1 each): shell version about $0.079 (2,675 output tokens); Java client at effort `medium` with no per-photo notes about $0.063 (946 output tokens). The saving is not established: one sample each, and quality at `medium` is untested. `VISION_EFFORT` default `medium` is a guess.
- Token prices (`VISION_PRICE_IN/OUT`, 2.00 and 10.00 per million) come from a cached table dated 2026-09-25, not a live lookup.
- Structured output (`output_config.format` json_schema) plus `effort` worked live on the first try with `claude-sonnet-5-5`. Behavior with other models (for example Haiku 4.5) is untested.
- Two-step variant (cheap role classification, then extraction per role) is not tried.
- HEIC resizing (`ImageResizer`) shells out to macOS `sips`. Throwaway and macOS-only.

## Not built yet
- Evidence builder (model observations + `ReleaseInfo` into `IdentifierEvidence`, including how to compare partial matrix text and barcode formatting).
- Runner, CSV input, report writer.
- Condition experiment.
