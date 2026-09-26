---
name: spark-scoring
description: "Run \"Spark scoring\", this project's release-quality bar. The app is scored 0–10 on seven weighted dimensions (spec coverage, correctness, code quality, UX/design, safety & privacy, performance, verification) from real evidence, and must score above 8.5 to pass. Use this whenever the user says \"spark scoring\", \"spark score\", \"run spark\", \"score the app\", \"is it good enough to ship\", \"rate the app out of 10\", asks for a release-readiness or quality gate check, or after a large feature/QA round when they want to know where the app stands. It also covers improving the score: fixing whatever drags a dimension down, then re-scoring."
---

# Spark scoring

Spark scoring is the release bar the owner defined for this app: an honest 0–10 score across seven
weighted dimensions. **The weighted total must be above 8.5.** The point is to find out where the app really
is, not to produce a flattering number. A score the evidence doesn't support is worse than a low one,
because it hides risk from someone about to ship.

## Core rules
- **Evidence or it didn't happen.** Every dimension score cites what you checked: CI run, test names,
  screenshot files, file/function names, measured numbers. If you couldn't check something, say so and let
  the cap rules below limit the score.
- **Caps are hard ceilings**, applied by the calculator script:
  - Performance can't exceed **8** while any spec target is unmeasured.
  - Verification can't exceed **7** without a real-device test of the flows that need hardware (for a
    two-phone app, a two-device run).
  - UX can't exceed **7** if the key screens haven't been seen as real screenshots.
- **Baseline first, then fix, then re-score.** Record the honest baseline before changing anything, so
  the improvement is visible and the owner can trust the delta.
- **Fix what drags a dimension below 8** when the fix is in reach; list what isn't.

## Workflow

### 1. Gather evidence (don't score from memory)
Read `references/rubric.md` for the scoring anchors (what a 6, 8 and 10 look like per dimension). Then collect:
- **Spec:** the product spec or README feature table. Check each must-have against the code.
- **Correctness:** read the diff since the last score (`git log`), hunt for real bugs in the changed flows,
  and check the failure paths the spec calls out.
- **Build & tests:** the latest CI run on the current commit (build, unit tests, emulator/UI tests), plus
  warnings in the build log.
- **UX:** current screenshots. In this repo, push a commit whose message contains `[screenshots]` and CI
  commits fresh emulator screenshots to `docs/screenshots/`. View them with the Read tool and look for
  overlaps, clipping, inconsistency and wordiness.
- **Safety & privacy:** safe-mode guardrails, permissions, data handling, privacy policy, consent.
- **Performance:** measured numbers only (e.g. release APK/AAB size from the CI "Sizes" step), each
  against the spec target.
- **Verification:** what actually ran where: unit tests, emulator tests, real devices.

### 2. Score each dimension
Give each a 0–10 score (halves allowed) and one or two lines of evidence. Be specific: "17 JVM unit tests
+ 2 emulator tests green on run 20" beats "tests pass".

### 3. Compute
Write the scores to a JSON file and run the calculator. It applies the weights and caps and prints the result:

```bash
python .claude/skills/spark-scoring/scripts/spark_score.py scores.json
```

Input format (flags drive the caps):
```json
{
  "label": "Run 2 — baseline",
  "scores": {"spec": 9, "correctness": 8.5, "code_quality": 9, "ux": 9,
             "safety": 9.5, "performance": 8, "verification": 6.5},
  "evidence": {"spec": "...", "correctness": "..."},
  "flags": {"unmeasured_performance_targets": true,
            "real_device_tested": false,
            "screenshots_reviewed": true}
}
```
It prints a markdown table (score, capped score, weight, contribution, evidence) and PASS/FAIL against 8.5.

### 4. Improve and re-score
If it fails, or any dimension is below 8, fix the highest-weight gaps you can: bugs, missing tests,
visual defects, missing guardrails. Verify each fix through CI, then run steps 1–3 again as "after fixes".

### 5. Record the run
Append a section to `docs/SPARK_SCORING.md`: `## Run N — YYYY-MM-DD`, with a table of baseline vs.
after-fix scores and evidence per dimension, the final total, and pass/fail. Commit it. The history is how
the owner tracks quality over time.

### 6. Report to the user
Lead with the total and pass/fail. Then show the per-dimension table (before → after), what you fixed, and
what still limits the score. Be candid about the biggest remaining risk: often something that simply
hasn't been tested on real hardware yet. If the pass is narrow, say it's narrow.

## Weights
| Dimension | Key | Weight |
|---|---|---|
| Spec coverage | `spec` | 20% |
| Correctness & robustness | `correctness` | 20% |
| Code quality & build health | `code_quality` | 10% |
| UX & visual design | `ux` | 20% |
| Safety & privacy | `safety` | 10% |
| Performance & NFRs | `performance` | 10% |
| Verification evidence | `verification` | 10% |
