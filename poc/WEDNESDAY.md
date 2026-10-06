# Pickup notes for the Wednesday test (written 2026-10-05, Monday)

Resume from here. State: everything is committed, 187 unit tests pass, and nothing paid has run since record 001 ($0.062). Details and caveats are in `OPEN_QUESTIONS.md`; the bars are in `../THRESHOLDS.md` (DRAFT, not approved).

## What the user brings (about 10 records)
- Photos in `records/NNN/` for identification: 11 or fewer per record, any file names, any order. Barcode, both runout sides, both labels, sleeve front and back.
- Condition photos in `records/NNN/condition/`: each side of the vinyl under a lamp, sleeve front and back. 8 or fewer. (Lamp photos means photos of the record surface under a lamp, not of a lamp.)
- `records.csv` rows: `id`, `media_grade`, `sleeve_grade`, and optionally `known_defects`, `listing_notes` (their own words for the Discogs comment), `cost_basis`, `bucket`. Leave `truth_release_id` blank; it is filled in after the shortlist.
- Lock the grades in `records.csv` and commit BEFORE the condition run, so the model and the user do not influence each other.
- Time themselves per record and fill `human_minutes`, `discogs_touches`, `annoyance` in the report.

## Run order (say the cost estimate and get a go before each paid step)
All commands: `java -cp "poc/target/classes:$(cat out/cp.txt)" dev.vinyl.poc.runner.<Class> ...` from the repo root. Run `cd poc && mvn -q -o compile` first if the code changed.
1. `Main` (paid). Identification, one vision call per record. Estimate $0.03 to $0.06 each, extrapolated from record 001's $0.062; not measured. Photo cap `POC_MAX_PHOTOS` (default 11).
2. `Shortlist` (free; add `--all` to see every fetched candidate). Narrows each record to the plausible Discogs releases in release id order with no scores, so the user can confirm the answer key from the physical record without anchoring on the tool's pick. The user then fills `truth_release_id`.
3. `Main` again (free, cached), then `SummaryMain --report <that report>` (free). Record 001 is excluded by default.
4. `ConditionMain` (paid, same rough estimate). Then the user marks the review sheet (`real` / `false_alarm` / `unsure` for observed defects; `found` / `missed` / `not_visible` for known ones) and `user_decision` (`keep` / `change`) in the condition report.
5. `ConditionMain --summary --review <marked sheet>` and `ConditionMain --notes --review <marked sheet>` (free). Each main run makes a NEW blank review sheet, so always pass `--review`.

## Decisions needed before the run
- Approve or change `THRESHOLDS.md`: identification bars 1 to 8 (bar 5 cost is proposed; bar 6 eBay pricing is under discussion) and condition bars C1 to C7.
- Postage: the runner uses a placeholder of $5.50 unless `POC_POSTAGE` is set (user says the real cost is usually a bit less). Record 001's cost basis of 20 is an assumed stand-in.

## Decisions to make from the data (after the run)
- Escalation limit: flat $5 now (`Thresholds.riskLimit`); replay the recorded gaps at several limits, consider a percentage rule. `Replay` is the free tool for stripped-evidence checks.
- One-wrong-digit barcode penalty (score drop 0.86 to 0.29 on the replay).
- How bar 6 should weight eBay by `ebay_match`; whether the eBay alert (2x and $20 above) is right; eBay figures are typed in by hand.
- Whether a one-step grade difference should count as a flag.

## The end-to-end listing (user wants it if all goes well)
- Not built and not tested. The POC has no publishing code. I believe the Discogs API can create listings, possibly as a draft, but have not tested it. Test a draft first, with the user's go-ahead, since it touches their account.
- A live listing can sell before it is deleted. Use a draft, or a record the user is happy to keep at a deliberately high price.
- Unverified: whether Discogs payment processing fees come on top of the 9% fee (the user confirmed the 9% applies to item plus shipping charged).

## Parked ideas (free experiments, none started)
- Identification as a paid service for people digging through records; guided photo capture. Check the Discogs API terms first. See the "Ideas" section of `OPEN_QUESTIONS.md`.
- Free experiment 1: run cheap local checks (blur, brightness, glare) on Wednesday's photos and see whether failures line up with flagged records.
- Free experiment 2: simulate a smaller photo set offline by dropping observations from the cached vision results of photos outside a subset (for example only the barcode and back cover), and see which records still identify. That is the real question behind the store-digger idea.

## Known weak spots
- Artist-and-title search fetches only the top 15 results and missed the true release in the replay.
- Photo role scoring maps `photo_N` to the sorted file listing; it breaks if photos change after the vision call.
- Failed records (over the photo cap, errors) print but are not rows in the report.
- Vision accuracy is measured on one record. Condition prompts and schema are untested on real photos.
