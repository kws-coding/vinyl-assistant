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

## Condition experiment bars (DRAFT, proposed 2026-10-05, not approved)
The tool suggests a visual-only grade; the user's grade is always the one used. These bars say whether the suggestions are useful enough to keep. Marking is done by the user in the review sheet.

| # | Metric | Pass | Judged only if |
|---|---|---|---|
| C1 | Media grade within one step of the user's | 70% or more of compared records | at least 8 records compared |
| C2 | Sleeve grade within one step of the user's | 70% or more | at least 8 compared |
| C3 | Media grade two or more steps from the user's, either direction | 20% or fewer | at least 8 compared |
| C4 | `cannot_tell` for the media grade | 30% or fewer of records | at least 8 records |
| C5 | False alarms among observed defects the user marked real or false alarm | 40% or fewer | at least 10 marked |
| C6 | Visible known defects the tool missed | 40% or fewer (defects marked `not_visible` are excluded) | at least 5 found or missed |
| C7 | Condition call cost per paid record | mean $0.06 or less, none above $0.10 | at least 1 paid record |

Reported, not judged:
- Direction of disagreement. A visual grade is expected to run above a grade that reflects play wear, so "suggested above yours" is not a failure by itself. "Suggested below yours" (damage the tool saw that you did not grade) is the more informative direction.
- How often a flag would have changed the user's grade (`user_decision` keep or change). If this is low, flags are noise.

Notes on the bars:
- Grades differ by a step between careful people, so within-one-step is the realistic target. Exact agreement is reported but has no bar.
- The user's own grade includes how the record plays, which photos cannot show. That limits how well any photo-based grade can agree.
- With about 10 records these bars are blunt, and C1 to C4 need 8 records with a grade on both sides.

## Condition note (Discogs comments)
Drafted from a fixed template in code, not by the model: reviewed defects, "none seen in photos" only when both sides were photographed, and the user's own `listing_notes`. Hard limits: 500 characters, no HTML (`<` and `>` are removed with a warning). It never mentions how the record plays unless the user wrote it. It is a draft for the user to read; nothing is posted.
