# Spark scoring

Afar's release bar. Each dimension is scored 0–10 by reviewing code, CI results and emulator
screenshots; the weighted total must be **above 8.5**. Scores are judgement calls backed by
evidence — cite it, and never score what hasn't been checked.

| # | Dimension | Weight | What earns a high score |
|---|---|---|---|
| 1 | Spec coverage | 20% | Every MVP must-have, review change and edge case from the product spec is implemented. |
| 2 | Correctness & robustness | 20% | Code review finds no known bugs; failure paths (drops, low battery, denied permissions) behave as specified; logic covered by tests. |
| 3 | Code quality & build health | 10% | CI green with no warnings of note; clear structure; unit tests on pure logic. |
| 4 | UX & visual design | 20% | Screens verified from real screenshots: consistent, modern, minimal text, no overlaps or clipped UI. |
| 5 | Safety & privacy | 10% | Safe mode guardrails in place and on by default; local-only data; clear consent. |
| 6 | Performance & NFRs | 10% | Measured against spec targets (APK < 15 MB, cold start, latency, battery). Unmeasured targets can't score above 8. |
| 7 | Verification evidence | 10% | Automated tests on emulator; real two-device testing for the linked flows. Emulator-only caps at 7. |

**How to run:** review the diff since the last score, check CI (build, unit tests, emulator UI
tests), refresh screenshots by pushing a commit with `[screenshots]` in its message, score each
dimension with evidence, fix anything that drags a dimension below 8, and re-score.

## Run 1 — 2026-09-25

| Dimension | Baseline | After fixes | Evidence |
|---|---|---|---|
| Spec coverage | 9.0 | 9.0 | All 10 must-haves, 5 review changes, edge-case table. H.264 replaced by adaptive JPEG (spec's "first prototype"); ad SDK not wired. |
| Correctness | 7.0 | 8.5 | Fixed: auto-disconnect while framing, stuck countdown, stale "saving" state, undelivered "session ended", lens-switch dead state (earlier). Two-phone flows still untested on hardware. |
| Code quality | 7.0 | 9.0 | CI green, deprecation removed, 17 JVM unit tests, 2 emulator UI tests. |
| UX & design | 8.0 | 9.0 | 9 emulator screenshots reviewed; fixed lock-screen overlap, all-caps device name, unbalanced home. Remote live-view screen needs a paired Camera, so not yet screenshot-verified. |
| Safety & privacy | 9.0 | 9.5 | Safe mode on by default: Camera-side Allow, LIVE sign over all overlays, enforced auto-disconnect, chimes, consent, offline-only. |
| Performance & NFRs | 7.5 | 8.0 | Release APK 2.1 MB (target < 15 MB). Cold start, preview latency and battery unmeasured. |
| Verification | 3.0 | 6.5 | Unit + emulator end-to-end for single-device flows; CameraX, Nearby and the foreground service start cleanly on Android 14. No two-phone run yet. |
| **Spark score** | **7.45** | **8.60** | **Pass (> 8.5)** — narrow margin; the two-phone hardware test is the biggest remaining risk. |
