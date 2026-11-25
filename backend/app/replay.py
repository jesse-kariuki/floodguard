"""Replay mode: what FloodGuard would have shown on a past date, from forecasts as issued at the time.

Reads assets/replay_daily.json.gz (built by scripts/export_replay.py from the backtest's archived Open-Meteo
forecasts). Bands are recomputed here with the live rules, so replay cannot drift from app/risk.py. Nothing in
this module touches subscribers or sends SMS.
"""
from __future__ import annotations

import gzip
import json
from dataclasses import dataclass
from datetime import date, timedelta
from functools import lru_cache

import numpy as np

from app.config import get_settings
from app.geo import get_store
from app.risk import BAND_NAMES, LEVEL_NAMES, MATRIX, UNMODELLED_TIER, apply_modifiers, rain_band

EVENT_LEAD_DAYS = 1         # an alert the day before a flood counts as a hit, as in scripts/backtest.py
EPISODE_SLACK_DAYS = 1      # an alert episode within a day of a flood counts as matching it, as in the backtest
TIER_CODES = {"High": "H", "Medium": "M", "Low": "L"}

SOURCE_NOTES = {
    "day_ahead": "Forecasts issued about a day before each hour. This is what a day's warning would really have "
                 "had to go on.",
    "latest_run": "The most recent forecast run before each hour. This is close to hourly live monitoring, at "
                  "short notice.",
}
CAVEATS = ("Real archived forecasts (Open-Meteo Previous Runs), assessed with the live alert rules. River "
           "discharge is left out because past forecasts of it are not available. The flood catalogue has 7 "
           "events from news reports and is incomplete, so an alert with no documented flood is not proof of a "
           "false alarm.")


class ReplayUnavailable(Exception):
    pass


@dataclass(frozen=True)
class Event:
    event_id: str
    start: date
    end: date
    description: str
    source_url: str
    date_evidence: str

    def json(self) -> dict:
        return {"event_id": self.event_id, "date_start": self.start, "date_end": self.end,
                "description": self.description, "source_url": self.source_url, "date_evidence": self.date_evidence}


class ReplayStore:
    def __init__(self, payload: dict):
        self.dates = [date.fromisoformat(d) for d in payload["dates"]]
        self.start, self.end = self.dates[0], self.dates[-1]
        self.zone_ids: list[str] = payload["zones"]
        self.sources: dict = payload["sources"]
        self.events = [Event(e["event_id"], date.fromisoformat(e["date_start"]), date.fromisoformat(e["date_end"]),
                             e["description"], e["source_url"], e.get("date_evidence", "")) for e in payload["events"]]
        # City-wide band per day: the worst zone. A day counts as an alert day if this is 1 or more.
        self.city_band = {name: [max((b for b in col if b is not None), default=None)
                                 for col in zip(*src["band"])] for name, src in self.sources.items()}

    def index(self, day: date) -> int:
        if not self.start <= day <= self.end:
            raise ValueError(f"Replay covers {self.start} to {self.end}.")
        return (day - self.start).days

    def alert_on(self, source: str, day: date) -> bool:
        if not self.start <= day <= self.end:
            return False
        return (self.city_band[source][self.index(day)] or 0) >= 1

    def zone_rows(self, source: str, day: date) -> list[dict]:
        """Each zone on `day`, at its worst hour, with the band recomputed from the live rules."""
        src, i, store = self.sources[source], self.index(day), get_store()
        rows = []
        for z, zone_id in enumerate(self.zone_ids):
            zone = store.zones.get(zone_id)
            area, own, prev72 = src["area"][z][i], src["own"][z][i], src["prev72"][z][i]
            row = {"zone_id": zone_id, "latitude": zone.lat if zone else None,
                   "longitude": zone.lon if zone else None, "available": area is not None}
            if area is None:
                rows.append({**row, "rain_band": 0, "rain_band_name": BAND_NAMES[0], "reasons": ["forecast_incomplete"]})
                continue
            band, reasons = apply_modifiers(rain_band(area), area, prev72, None, None)
            if band and own is not None and area > own:
                reasons.append("rain_nearby")
            rows.append({**row, "rain_band": band, "rain_band_name": BAND_NAMES[band], "rain_next_24h_mm": own,
                         "rain_area_24h_mm": area, "rain_prev_72h_mm": prev72, "issue_hour": src["hour"][z][i],
                         "reasons": reasons})
        return rows

    def event_context(self, source: str, day: date) -> dict | None:
        """The documented flood this date belongs to, if any, and whether the system caught it."""
        for e in self.events:
            if not e.start - timedelta(days=EVENT_LEAD_DAYS) <= day <= e.end + timedelta(days=EPISODE_SLACK_DAYS):
                continue
            window = [e.start - timedelta(days=EVENT_LEAD_DAYS) + timedelta(days=k)
                      for k in range((e.end - e.start).days + EVENT_LEAD_DAYS + 1)]
            alerts = [d for d in window if self.alert_on(source, d)]
            relation = "day_before" if day < e.start else "after" if day > e.end else "during"
            return {**e.json(), "relation": relation, "detected": bool(alerts),
                    "first_alert_date": alerts[0] if alerts else None,
                    "alerted_day_before": self.alert_on(source, e.start - timedelta(days=1))}
        return None

    def event_summaries(self, source: str) -> list[dict]:
        out = []
        for e in self.events:
            ctx = self.event_context(source, e.start)
            out.append({**e.json(), "detected": ctx["detected"], "first_alert_date": ctx["first_alert_date"],
                        "alerted_day_before": ctx["alerted_day_before"]})
        return out


def verdict(alert: bool, event: dict | None) -> tuple[str, str]:
    """A short, honest label for the replayed day."""
    if event and event["relation"] != "after":
        if alert:
            return "hit", "Flood documented: an alert was showing."
        if event["detected"]:
            return "hit_other_day", f"Flood documented: alert first showed on {event['first_alert_date']}."
        return "miss", "Flood documented, but no alert fired. Forecasts did not show enough rain."
    if alert and event:
        return "hit", "Alert on the day after a documented flood."
    if alert:
        return "no_documented_flood", ("Alert, but no flood in the catalogue. Possibly a false alarm; the "
                                       "catalogue is incomplete.")
    return "quiet", "No alert. Forecasts showed under 20 mm in 24 h everywhere."


@lru_cache
def get_replay() -> ReplayStore:
    path = get_settings().assets_dir / "replay_daily.json.gz"
    if not path.exists():
        raise ReplayUnavailable("Replay data not built. Run scripts.backtest, then scripts.export_replay.")
    return ReplayStore(json.loads(gzip.decompress(path.read_bytes())))


@lru_cache
def _segment_index() -> tuple[np.ndarray, np.ndarray]:
    store = get_store()
    zone_pos = {z: i for i, z in enumerate(get_replay().zone_ids)}
    zones = np.array([zone_pos.get(z, -1) for z in store.zone])
    tiers = np.array([list(MATRIX).index(t) if t in MATRIX else -1 for t in store.tier])
    return zones, tiers


def levels_by_zone(rows: list[dict]) -> dict[str, dict[str, int] | None]:
    """{zone_id: {tier code: alert level}}. U is a segment the model could not score. None = no data."""
    out: dict[str, dict[str, int] | None] = {}
    for r in rows:
        if not r["available"]:
            out[r["zone_id"]] = None
            continue
        levels = {code: MATRIX[tier][r["rain_band"]] for tier, code in TIER_CODES.items()}
        out[r["zone_id"]] = {**levels, "U": MATRIX[UNMODELLED_TIER][r["rain_band"]]}
    return out


def segments_by_level(rows: list[dict]) -> dict[str, int]:
    """How many road segments sit at each alert level, per their own tier, as the map colours them."""
    zones, tiers = _segment_index()
    tier_rows = [MATRIX[t] for t in MATRIX] + [MATRIX[UNMODELLED_TIER]]     # index -1 picks the unmodelled row
    counts = dict.fromkeys([*LEVEL_NAMES.values(), "unknown"], 0)
    for z, r in enumerate(rows):
        in_zone = zones == z
        if not r["available"]:
            counts["unknown"] += int(in_zone.sum())
            continue
        for t, row in enumerate(tier_rows):
            n = int((in_zone & (tiers == (t if t < len(MATRIX) else -1))).sum())
            counts[LEVEL_NAMES[row[r["rain_band"]]]] += n
    return counts
