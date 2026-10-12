import json
import math
import statistics
import time
from pathlib import Path


DATA_DIR = Path("data")

STRESS_NORMALIZATION = [
    (19.381044, 9.474176),
    (5.5441837, 1.9308523),
    (184.17795, 54.48858),
    (33.487267, 25.893078),
    (32.97501, 16.267557),
    (677.911, 130.61485),
    (0.43178082, 0.1747625),
    (147.99915, 170.24158),
    (208.32425, 245.64342),
    (37.63596, 19.478745),
]
STRESS_INIT = 3.835272
STRESS_COEFFICIENTS = [
    0.16605523,
    0.24399279,
    0.0,
    0.0,
    -0.07095941,
    -0.20609115,
    0.0,
    -0.14579488,
    -0.09786916,
    0.0,
]


def _read_json(path: Path, fallback):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return fallback


def _read_jsonl(path: Path) -> list[dict]:
    rows = []
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                rows.append(json.loads(line))
    except Exception:
        pass
    return rows


def _valid_summary_row(row: dict) -> bool:
    return bool(
        row.get("downloaded_step_minutes")
        or row.get("downloaded_sleep_segments")
        or row.get("heart_rate_samples")
        or row.get("spo2_samples")
        or row.get("sleep_minutes")
    )


def _write_json(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _append_jsonl(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(payload, sort_keys=True) + "\n")


def _mean(values: list[float], default=None):
    return sum(values) / len(values) if values else default


def _clamp(value: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, value))


def _score_label(value: float) -> str:
    if value >= 67:
        return "green"
    if value >= 34:
        return "yellow"
    return "red"


def _score_sigmoid(x: float, k: float, base: float, midpoint: float, anchor: float) -> float:
    max_exp = 709.0
    numerator = 1 + math.exp(min(max_exp, k * (anchor - midpoint)))
    denominator = 1 + math.exp(min(max_exp, k * (x - midpoint)))
    return base * numerator / denominator


def _numeric(value):
    return value if isinstance(value, (int, float)) and math.isfinite(value) else None


def _ewma(values: list[float], tau_days: float) -> float | None:
    alpha = 1.0 - math.exp(-1.0 / max(1.0, tau_days))
    acc = None
    for value in values:
        value = _numeric(value)
        if value is None:
            continue
        acc = value if acc is None else acc + alpha * (value - acc)
    return acc


def _trend_delta(values: list[float], short: int, long: int) -> float | None:
    clean = [_numeric(v) for v in values]
    clean = [v for v in clean if v is not None]
    if len(clean) < 2:
        return None
    recent = _mean(clean[-short:], clean[-1])
    baseline = _mean(clean[-long:], recent)
    if recent is None or baseline is None:
        return None
    return recent - baseline


def _hours_past_noon(ts: int | float | None) -> float | None:
    if not ts:
        return None
    local = time.localtime(ts)
    hours = local.tm_hour + local.tm_min / 60.0 + local.tm_sec / 3600.0
    if hours < 12.0:
        hours += 24.0
    return hours - 12.0


def _sleep_duration_score(minutes: float) -> int:
    hours = max(0.0, minutes / 60.0)
    if 7.0 <= hours <= 9.0:
        return 100
    if hours < 7.0:
        raw = _score_sigmoid(hours, k=-1.5, base=100, midpoint=5.0, anchor=7.0)
        return round(_clamp(raw, 0, 100))
    raw = _score_sigmoid(hours, k=0.8, base=100, midpoint=11.0, anchor=9.0)
    return round(_clamp(max(50, raw), 0, 100))


def _sleep_consistency_score(history: list[dict], sleep_segments: list[dict]) -> int | None:
    starts = [
        _hours_past_noon(row.get("sleep_window_start_ts"))
        for row in history
        if row.get("sleep_window_start_ts")
    ]
    starts = [v for v in starts if v is not None]
    distinct_starts = []
    for start in starts:
        if not any(abs(start - seen) < (5 / 60.0) for seen in distinct_starts):
            distinct_starts.append(start)
    current_start = min((row.get("start") for row in sleep_segments if row.get("start")), default=None)
    current = _hours_past_noon(current_start)
    if current is None or len(distinct_starts) < 3:
        return None
    baseline = statistics.median(distinct_starts[-14:])
    diff_minutes = (current - baseline) * 60
    if diff_minutes > 15:
        penalty = ((diff_minutes - 15) / 105) * 100
    elif diff_minutes < -15:
        penalty = min(20, ((abs(diff_minutes) - 15) / 105) * 100)
    else:
        penalty = 0
    return round(_clamp(100 - penalty, 0, 100))


def _sleep_fragmentation_score(sleep_segments: list[dict], sleep_minutes: float) -> int:
    if sleep_minutes <= 0:
        return 0
    starts = sorted(
        (row.get("start"), row.get("end"))
        for row in sleep_segments
        if row.get("start") and row.get("end") and row.get("end") > row.get("start")
    )
    if len(starts) <= 1:
        return 100
    significant_gaps = 0
    gap_minutes = 0.0
    for (_prev_start, prev_end), (start, _end) in zip(starts, starts[1:]):
        gap = max(0, start - prev_end) / 60.0
        if gap >= 5:
            significant_gaps += 1
            gap_minutes += gap
    gap_penalty = min(80, max(0, gap_minutes - 20) / 70 * 80)
    frequency_penalty = min(20, max(0, significant_gaps - 1) * 5)
    return round(_clamp(100 - gap_penalty - frequency_penalty, 0, 100))


def _sleep_stage_score(deep_minutes: float, rem_minutes: float) -> int | None:
    if deep_minutes <= 0 and rem_minutes <= 0:
        return None
    deep_score = _clamp(100 * deep_minutes / 90.0, 0, 100)
    rem_score = _clamp(100 * rem_minutes / 90.0, 0, 100)
    return round(0.5 * deep_score + 0.5 * rem_score)


def _sleep_interruptions_score(awake_minutes: float, awakening_durations: list[float]) -> int:
    duration_score = 80.0
    if awake_minutes > 20:
        duration_score = max(0, 80.0 - ((awake_minutes - 20) / 70.0) * 80.0)
    significant = sum(1 for duration in awakening_durations if duration > 5)
    frequency_fractions = (1.0, 1.0, 0.75, 0.5, 0.0)
    frequency_score = 20.0 * frequency_fractions[min(significant, len(frequency_fractions) - 1)]
    return round(_clamp(duration_score + frequency_score, 0, 100))


def _sleep_stage_breakdown(details: dict) -> dict:
    counts = {int(k): int(v) for k, v in (details.get("stage_counts") or {}).items()}
    stages = sorted(details.get("stages") or [], key=lambda row: row.get("timestamp") or 0)
    if stages and not counts:
        counts = {}
        for row in stages:
            stage = int(row.get("stage", 0) or 0)
            counts[stage] = counts.get(stage, 0) + 1

    awakening_durations = []
    current_awake = 0
    for row in stages:
        stage = int(row.get("stage", 0) or 0)
        if stage == 4:
            current_awake += 1
        elif current_awake:
            awakening_durations.append(current_awake)
            current_awake = 0
    if current_awake:
        awakening_durations.append(current_awake)

    return {
        "light_minutes": counts.get(1, 0) + counts.get(5, 0),
        "rem_minutes": counts.get(2, 0),
        "deep_minutes": counts.get(3, 0),
        "awake_minutes": counts.get(4, 0),
        "nap_minutes": counts.get(5, 0),
        "awakening_durations": awakening_durations,
        "raw_counts": {str(k): v for k, v in sorted(counts.items())},
    }


def _sleep_quality_model(sleep_minutes: float, sleep_segments: list[dict], history: list[dict],
                         stage_breakdown: dict | None = None) -> dict:
    duration = _sleep_duration_score(sleep_minutes)
    fragmentation = _sleep_fragmentation_score(sleep_segments, sleep_minutes)
    consistency = _sleep_consistency_score(history, sleep_segments)
    stages = None
    interruptions = fragmentation
    if stage_breakdown:
        stages = _sleep_stage_score(stage_breakdown.get("deep_minutes", 0), stage_breakdown.get("rem_minutes", 0))
        interruptions = _sleep_interruptions_score(
            stage_breakdown.get("awake_minutes", 0),
            stage_breakdown.get("awakening_durations") or [],
        )

    weighted = [("duration", duration, 0.40), ("interruptions", interruptions, 0.20)]
    if stages is not None:
        weighted.append(("stages", stages, 0.20))
    else:
        weighted[0] = ("duration", duration, 0.55)
        weighted[1] = ("interruptions", interruptions, 0.25)
    if consistency is not None:
        weighted.append(("consistency", consistency, 0.20))
    else:
        weighted = [(name, value, weight / 0.80) for name, value, weight in weighted]
    overall = sum(value * weight for _name, value, weight in weighted) / sum(weight for _name, _value, weight in weighted)
    return {
        "score": round(_clamp(overall, 0, 100)),
        "duration": duration,
        "stages": stages,
        "consistency": consistency,
        "interruptions": interruptions,
        "fragmentation": fragmentation,
        "stage_breakdown": stage_breakdown or {},
        "method": "Open Wearables-inspired duration + deep/REM stages + consistency + awake interruptions",
    }


def _classify_training_load(acute: float | None, chronic: float | None) -> dict:
    if acute is None or chronic is None or chronic <= 0:
        return {
            "label": "collecting",
            "recommendation": "Collect more connected syncs before judging load balance.",
            "ratio": None,
            "balance": None,
        }

    ratio = acute / chronic
    balance = chronic - acute
    if ratio < 0.70:
        label = "underloaded"
        recommendation = "Load is below recent baseline; good window for easy volume if recovery allows."
    elif ratio <= 1.30:
        label = "productive"
        recommendation = "Acute load is close to chronic load; maintain or progress gradually."
    elif ratio <= 1.55:
        label = "strained"
        recommendation = "Acute load is elevated; bias toward sleep and lower intensity."
    else:
        label = "overreaching"
        recommendation = "Acute load is far above baseline; treat this as a recovery-priority day."
    return {
        "label": label,
        "recommendation": recommendation,
        "ratio": round(ratio, 2),
        "balance": round(balance, 1),
    }


def _stress_zone_summary(stress_preview: dict, live_hrv: dict | None = None) -> dict:
    rows = stress_preview.get("stress") or []
    zone_minutes = {"low": 0.0, "medium": 0.0, "high": 0.0}
    high_windows = []
    scores = []
    latest = None

    for row in rows:
        score = _numeric(row.get("score"))
        if score is None or score <= 0:
            continue
        start_ms = _numeric(row.get("start_ms"))
        end_ms = _numeric(row.get("end_ms"))
        minutes = 1.0
        if start_ms and end_ms and end_ms > start_ms:
            minutes = _clamp((end_ms - start_ms) / 60000.0, 1.0, 60.0)

        if score <= 29:
            zone = "low"
        elif score <= 59:
            zone = "medium"
        else:
            zone = "high"
        zone_minutes[zone] += minutes
        scores.append(score)
        latest = row
        if zone == "high":
            high_windows.append({
                "start_local": row.get("start_local"),
                "end_local": row.get("end_local"),
                "minutes": round(minutes),
                "score": round(score),
                "level": row.get("level"),
            })

    total = sum(zone_minutes.values())
    avg_score = _mean(scores)
    if avg_score is None:
        return {
            "source": "unavailable",
            "sample_count": 0,
            "zone_minutes": {name: 0 for name in zone_minutes},
            "message": "Stress zones need an RRI/stress sync or a live 60-second RRI measurement.",
            "method": "Huawei RRI/stress file parser with score buckets: low <=29, medium <=59, high >=60",
        }

    low_pct = round(100 * zone_minutes["low"] / total) if total else 0
    high_pct = round(100 * zone_minutes["high"] / total) if total else 0
    label = "low" if avg_score <= 29 else "medium" if avg_score <= 59 else "high"
    return {
        "source": "rri_stress_file" if rows else "live_rri",
        "sample_count": len(scores),
        "avg_score": round(avg_score, 1),
        "max_score": round(max(scores), 1),
        "latest_score": latest.get("score") if latest else None,
        "latest_level": latest.get("level") if latest else None,
        "label": label,
        "gauge_0_3": round(_clamp(avg_score / 30.0, 0, 3), 1),
        "zone_minutes": {name: round(value) for name, value in zone_minutes.items()},
        "low_pct": low_pct,
        "high_pct": high_pct,
        "high_windows": high_windows[-5:],
        "message": (
            f"{low_pct}% of tracked stress time was low stress; "
            f"high stress accounted for {high_pct}%."
        ),
        "method": "Huawei RRI/stress file parser with score buckets: low <=29, medium <=59, high >=60",
    }


def build_insights(data_dir: Path = DATA_DIR) -> dict:
    return {"status": "ok"}
