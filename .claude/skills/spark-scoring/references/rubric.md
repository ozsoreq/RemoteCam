# Spark scoring rubric: anchors per dimension

Use these anchors to keep scores consistent between runs. Half points are fine. When unsure between two
anchors, pick the lower one and name what would earn the higher one.

## 1. Spec coverage (20%)
- **10:** every must-have, every recommended change and every edge case from the spec is implemented,
  and any deviations were agreed.
- **8:** all must-haves; a few secondary items deferred with a documented reason.
- **6:** a must-have missing or only partly working.
- **≤4:** core flow incomplete.
Check: walk the spec's feature table and edge-case table against the code, item by item.

## 2. Correctness & robustness (20%)
- **10:** a fresh review of the changed flows finds no bugs; failure paths (link drop, low battery,
  permission denied, storage full, backgrounding) behave as specified; logic has unit tests.
- **8:** no known bugs in core flows; minor edge-case gaps documented.
- **6:** a known bug in a core flow, or failure paths unhandled.
- **≤4:** crashes or data loss in normal use.
Check: read the diff since the last run; trace at least the main user flow end to end.

## 3. Code quality & build health (10%)
- **10:** CI green on the current commit, no meaningful warnings, clear structure, pure logic unit-tested,
  UI smoke-tested.
- **8:** CI green, some warnings or thin tests.
- **6:** CI flaky or red, or no tests.

## 4. UX & visual design (20%)
- **10:** every key screen seen as a real screenshot: consistent design system, minimal text, no
  overlaps, clipping or dead ends, accessible labels.
- **8:** screenshots show minor polish issues.
- **7 (cap):** key screens not seen as screenshots. You can't score what you haven't looked at.
- **≤6:** visible defects on primary screens.

## 5. Safety & privacy (10%)
- **10:** protections for misuse on by default and hard to bypass; local-only data or clearly disclosed
  processing; consent; privacy policy in-app and published; permissions minimal and explained.
- **8:** guardrails present with small gaps.
- **≤6:** an exploitable misuse path or undisclosed data handling.

## 6. Performance & NFRs (10%)
- **10:** every spec target (size, cold start, latency, frame rate, battery) measured and met.
- **8 (cap while anything is unmeasured):** measured targets met, others not yet measured.
- **≤6:** a measured target missed.

## 7. Verification evidence (10%)
- **10:** unit + emulator tests green, and the hardware-dependent flows tested on real devices
  (for a two-phone app, a two-device session).
- **7 (cap without real-device testing):** automated tests on emulator only.
- **≤5:** only unit tests, or nothing run.

## Pass rule
Weighted total > 8.5 = PASS. Report narrow passes (≤ 0.2 over) as narrow, and name the biggest remaining risk.
