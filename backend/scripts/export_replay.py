"""Turn the backtest's archived forecasts into the small daily table that replay mode serves.

Replay answers "what would FloodGuard have shown on this date?" with forecasts as they were issued at the time,
so it needs no hindsight and no network at runtime. Run after `python -m scripts.backtest` has filled
evidence/cache (the cache is not committed; this asset is):

    python -m scripts.export_replay         # from backend/; writes assets/replay_daily.json.gz

Replay runs up to yesterday (Nairobi time). Days after the backtest period (scripts.backtest END) are fetched into
their own cache files, named by date range so a later run never reuses a stale range. Those extra days are for replay
only: the scored backtest period and evidence/VALIDATION.md stay fixed.

For every zone, every hour is assessed with the live rules in app/risk.py: the 24 h forecast from that hour,
the wettest forecast within NEIGHBOUR_RADIUS_KM, and the wet-ground modifier. River discharge is left out because
past forecasts of it are not available. Each day keeps its worst hour, the same "alert at any hour that day" rule
the backtest scores, so replay and evidence/VALIDATION.md describe the same alerts.
"""
from __future__ import annotations

import csv
import gzip
import json
import math
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
import numpy as np

from app.geo import GeoStore
from app.risk import NEIGHBOUR_RADIUS_KM, apply_modifiers, rain_band
from scripts.backtest import BACKEND, BATCH, END, EVIDENCE, PREVIOUS_RUNS, START, fetch, load_series, rolling_sum

ASSETS = BACKEND / "assets"
OUT = ASSETS / "replay_daily.json.gz"
SOURCES = {
    "day_ahead": ("precipitation_previous_day1", "Forecast issued about a day ahead"),
    "latest_run": ("precipitation", "Most recent forecast run (short notice)"),
}


def replay_end() -> date:
    return datetime.now(ZoneInfo("Africa/Nairobi")).date() - timedelta(days=1)


def load_extended(client: httpx.Client, zones: list[dict], variable: str, name: str,
                  last_day: date) -> tuple[list[str], np.ndarray]:
    """The backtest period from its cache, plus END+1 .. last_day+1 fetched separately. The extra day after
    last_day lets 24 h windows that start late on last_day complete."""
    times, rain = load_series(client, PREVIOUS_RUNS, zones, variable, f"prev_{name}")
    if last_day <= END:
        return times, rain
    a, b = END + timedelta(days=1), last_day + timedelta(days=1)
    rows: list[list[float]] = []
    ext_times: list[str] = []
    for i in range(0, len(zones), BATCH):
        batch = zones[i:i + BATCH]
        payload = fetch(client, PREVIOUS_RUNS, {
            "latitude": ",".join(str(z["lat"]) for z in batch),
            "longitude": ",".join(str(z["lon"]) for z in batch),
            "hourly": "precipitation,precipitation_previous_day1", "start_date": a.isoformat(),
            "end_date": b.isoformat(), "timezone": "Africa/Nairobi"}, f"prevext_{a}_{b}_{i}")
        for loc in payload:
            ext_times = loc["hourly"]["time"]
            rows.append([np.nan if v is None else float(v) for v in loc["hourly"][variable]])
    if times[-1] >= ext_times[0]:
        raise RuntimeError("Extension overlaps the backtest period; check END and the cache.")
    return times + ext_times, np.hstack([rain, np.array(rows, dtype=float)])


def _r(x: float) -> float | None:
    return None if math.isnan(x) else round(float(x), 1)


def daily_worst(times: list[str], rain: np.ndarray, zone_ids: list[str], neighbours: dict[str, list[str]]) -> dict:
    """Assess every zone-hour with the live rules, then keep each day's worst hour per zone."""
    next_24 = rolling_sum(rain, 24)
    prev_72 = np.hstack([np.full((rain.shape[0], 72), np.nan), rolling_sum(rain, 72)[:, :-72]])
    index = {z: i for i, z in enumerate(zone_ids)}
    near = [[index[n] for n in neighbours[z]] for z in zone_ids]
    # A zone with no usable forecast of its own is unavailable, as live; neighbours only count when available.
    area = np.array([np.nanmax(next_24[idx], axis=0) if len(idx) else next_24[i] for i, idx in enumerate(near)])
    area[np.isnan(next_24)] = np.nan

    days = sorted({t[:10] for t in times})
    day_of = np.searchsorted(np.array(days), np.array([t[:10] for t in times]))
    out = {k: [[None] * len(days) for _ in zone_ids] for k in ("band", "area", "own", "prev72", "hour")}
    for z in range(len(zone_ids)):
        best: dict[int, tuple[int, float, int]] = {}
        for h in np.flatnonzero(~np.isnan(area[z])):
            # Assess the rounded values that are stored and shown, so 19.97 mm can never display as 20.0 mm
            # with no alert, or the other way round.
            a = round(float(area[z, h]), 1)
            p72 = _r(prev_72[z, h])
            band, _ = apply_modifiers(rain_band(a), a, p72, None, None)
            d = int(day_of[h])
            if d not in best or (band, a) > best[d][:2]:
                best[d] = (band, a, int(h))
        for d, (band, a, h) in best.items():
            out["band"][z][d] = band
            out["area"][z][d] = round(a, 1)
            out["own"][z][d] = _r(next_24[z, h])
            out["prev72"][z][d] = _r(prev_72[z, h])
            out["hour"][z][d] = datetime.fromisoformat(times[h]).hour
    return {"days": days, **out}


def main() -> None:
    store = GeoStore(ASSETS)
    zones = json.loads((ASSETS / "zones.json").read_text())["zones"]
    zone_ids = [z["zone_id"] for z in zones]
    neighbours = store.neighbours(NEIGHBOUR_RADIUS_KM)
    with open(EVIDENCE / "flood_events.csv", encoding="utf-8", newline="") as fh:
        events = list(csv.DictReader(fh))

    last_day = replay_end()
    payload: dict = {"period": [START.isoformat(), last_day.isoformat()], "scored_backtest_end": END.isoformat(), "timezone": "Africa/Nairobi",
                     "zones": zone_ids, "neighbour_radius_km": NEIGHBOUR_RADIUS_KM, "events": events, "sources": {}}
    with httpx.Client(headers={"User-Agent": "FloodGuard-backtest/0.1"}) as client:
        for name, (variable, label) in SOURCES.items():
            # Same cache files as scripts.backtest, so this makes no requests once the backtest has run.
            times, rain = load_extended(client, zones, variable, name, last_day)
            table = daily_worst(times, rain, zone_ids, neighbours)
            keep = sum(d <= last_day.isoformat() for d in table["days"])     # drop the look-ahead day
            table = {k: v[:keep] if k == "days" else [row[:keep] for row in v] for k, v in table.items()}
            payload.setdefault("dates", table.pop("days"))
            payload["sources"][name] = {"label": label, **table}
            alert_days = sum(any((b or 0) >= 1 for b in col) for col in zip(*table["band"]))
            print(f"{name}: {alert_days} city-wide alert days over {len(payload['dates'])} days")

    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    OUT.write_bytes(gzip.compress(raw, compresslevel=9, mtime=0))
    print(f"wrote {OUT.relative_to(BACKEND)}: {len(raw) / 1e6:.1f} MB raw, {OUT.stat().st_size / 1e3:.0f} kB gzipped")


if __name__ == "__main__":
    main()
