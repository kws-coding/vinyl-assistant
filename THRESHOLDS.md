# POC pass/fail thresholds

Status: DRAFT, proposed 2026-10-05. Becomes final when the user approves it, before the full run. Do not change after seeing results.

Baseline: about 15 min by hand per record, of which about 5 min is grading. The tool never grades.

## Definitions
- High value: value at risk (price gap between plausible candidates, weighted by match) of $25 or more. User-confirmed 2026-10-05.
- Unflagged error: the tool said CONFIDENT and the top release is not `truth_release_id`.
- Human minutes: first photo to approved listing, grading included.

## Bars
| # | Metric | Pass |
|---|---|---|
| 1 | Unflagged high-value errors (hard gate) | 0 |
| 2 | Human minutes per record | median 10 or less |
| 3 | Correct-or-flagged, per bucket | easy 90%+; ambiguous, price_sensitive, runout_dependent 80%+; edge reported, no bar |
| 4 | Unflagged wrong, any value | at most 1 in 10 records |
| 5 | AI cost per record (first vision call only; cached reruns cost nothing) | mean $0.07 or less, none above $0.10. Proposed 2026-10-05, not yet approved. Sized to the 11-photo cap: record 001 (11 photos) cost $0.062. |
| 6 | Pricing error vs `ebay_sold_avg` | UNDER DISCUSSION (eBay is a weak yardstick; see OPEN_QUESTIONS.md). Candidate: a record passes if within $3 or 25%, whichever is larger; 70%+ of records with a comp pass; at least 5 comps; signed median gap reported separately. |
| 7 | Photo role accuracy | 90%+, only if `photo_roles.csv` exists |
| 8 | Effort: Discogs touches and annoyance (1 to 5), logged per record | median 1 touch or fewer, median annoyance 2 or lower |

## Settings fixed for this run
- Photo cap: 11 per record (`POC_MAX_PHOTOS`), set by the user 2026-10-05 and expected to come down later. A record over the cap is not sent to the vision model and is reported as failed.

## Notes
- Primary metric is bar 2, subject to bar 1.
- Bar 8 is self-reported and subjective.
- Small samples make these bars blunt; record 001 is excluded (easy case, no comp, no timing).
- The in-code escalation limit (`Thresholds.riskLimit`) is a separate setting from the $25 test cutoff above.
