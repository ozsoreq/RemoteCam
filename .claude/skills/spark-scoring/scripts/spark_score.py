#!/usr/bin/env python3
"""Spark scoring calculator: applies weights and hard caps, prints a markdown report.

Usage: python spark_score.py scores.json
Exit code 0 = PASS (> 8.5), 1 = FAIL, 2 = bad input.
"""
import json
import sys

PASS_MARK = 8.5
DIMENSIONS = [
    # key, label, weight
    ("spec", "Spec coverage", 0.20),
    ("correctness", "Correctness & robustness", 0.20),
    ("code_quality", "Code quality & build health", 0.10),
    ("ux", "UX & visual design", 0.20),
    ("safety", "Safety & privacy", 0.10),
    ("performance", "Performance & NFRs", 0.10),
    ("verification", "Verification evidence", 0.10),
]


def caps_for(flags):
    """Hard ceilings: you can't score what you haven't measured, seen or run."""
    caps = {}
    if flags.get("unmeasured_performance_targets", True):
        caps["performance"] = (8.0, "spec targets not all measured")
    if not flags.get("real_device_tested", False):
        caps["verification"] = (7.0, "no real-device test of hardware flows")
    if not flags.get("screenshots_reviewed", False):
        caps["ux"] = (7.0, "key screens not reviewed as screenshots")
    return caps


def compute(data):
    scores = data.get("scores", {})
    evidence = data.get("evidence", {})
    missing = [k for k, _, _ in DIMENSIONS if k not in scores]
    if missing:
        raise ValueError(f"missing scores for: {', '.join(missing)}")
    caps = caps_for(data.get("flags", {}))
    rows, total = [], 0.0
    for key, label, weight in DIMENSIONS:
        raw = float(scores[key])
        if not 0 <= raw <= 10:
            raise ValueError(f"{key} must be 0–10, got {raw}")
        cap, why = caps.get(key, (10.0, ""))
        final = min(raw, cap)
        contribution = final * weight
        total += contribution
        rows.append((label, raw, final, weight, contribution, why, evidence.get(key, "")))
    return rows, round(total, 2)


def report(data, rows, total):
    out = [f"### {data.get('label', 'Spark score')}", "",
           "| Dimension | Score | Counted | Weight | Contribution | Evidence |",
           "|---|---|---|---|---|---|"]
    for label, raw, final, weight, contribution, why, ev in rows:
        counted = f"{final:g}" + (f" (capped: {why})" if final < raw else "")
        out.append(f"| {label} | {raw:g} | {counted} | {weight:.0%} | {contribution:.2f} | {ev} |")
    verdict = "PASS" if total > PASS_MARK else "FAIL"
    margin = total - PASS_MARK
    note = " (narrow pass)" if verdict == "PASS" and margin <= 0.2 else ""
    out += ["", f"**Spark score: {total:.2f} / 10 → {verdict}{note}** (bar: > {PASS_MARK})"]
    weakest = sorted(rows, key=lambda r: r[2])[:2]
    out.append("Lowest dimensions: " + ", ".join(f"{r[0]} ({r[2]:g})" for r in weakest))
    return "\n".join(out), verdict


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    try:
        with open(sys.argv[1]) as f:
            data = json.load(f)
        rows, total = compute(data)
    except (OSError, ValueError, json.JSONDecodeError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    text, verdict = report(data, rows, total)
    print(text)
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
