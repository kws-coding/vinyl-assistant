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
| 5 | AI cost per record | mean $0.15 or less, none above $0.30 |
| 6 | Pricing error vs `ebay_sold_avg` | median within 20%, only records with a comp, at least 5 |
| 7 | Photo role accuracy | 90%+, only if `photo_roles.csv` exists |
| 8 | Effort: Discogs touches and annoyance (1 to 5), logged per record | median 1 touch or fewer, median annoyance 2 or lower |

## Notes
- Primary metric is bar 2, subject to bar 1.
- Bar 8 is self-reported and subjective.
- Small samples make these bars blunt; record 001 is excluded (easy case, no comp, no timing).
- The in-code escalation limit (`Thresholds.riskLimit`) is a separate setting from the $25 test cutoff above.
