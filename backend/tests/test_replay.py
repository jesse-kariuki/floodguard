"""Replay mode: past dates replayed through the live alert rules, read-only."""
from __future__ import annotations

import gzip
import json
from datetime import date, timedelta

import pytest

from app import replay as replay_mod
from app.config import get_settings
from app.geo import get_store
from app.risk import MATRIX, rain_band

START = date(2026, 4, 20)
DAYS = 10
WET_ZONE = 3                                # index of the one zone that gets rain in the synthetic table
EVENTS = [  # E1 is caught the day before; E2 is missed
    {"event_id": "E1", "date_start": "2026-04-27", "date_end": "2026-04-28", "date_evidence": "test",
     "description": "Test flood", "source_url": "https://example.org/e1"},
    {"event_id": "E2", "date_start": "2026-04-23", "date_end": "2026-04-23", "date_evidence": "test",
     "description": "Missed flood", "source_url": "https://example.org/e2"},
]
# Wettest-nearby 24 h rain in WET_ZONE per day; every other zone stays at 5 mm.
WET_AREA = {0: 25.0, 6: 25.0, 7: 60.0, 8: 5.0, 9: 5.0}
SATURATED_DAY, UNAVAILABLE_DAY = 4, 5


def synthetic_payload() -> dict:
    zones = list(get_store().zones)
    area = [[5.0] * DAYS for _ in zones]
    prev72 = [[0.0] * DAYS for _ in zones]
    for d, mm in WET_AREA.items():
        area[WET_ZONE][d] = mm
    area[WET_ZONE][SATURATED_DAY], prev72[WET_ZONE][SATURATED_DAY] = 15.0, 60.0     # wet ground lifts 15 mm to a band
    area[0][UNAVAILABLE_DAY] = None
    band = [[None if a is None else rain_band(a) for a in row] for row in area]
    band[WET_ZONE][SATURATED_DAY] = 1
    src = {"label": "test", "band": band, "area": area, "own": [list(r) for r in area], "prev72": prev72,
           "hour": [[6] * DAYS for _ in zones]}
    return {"dates": [(START + timedelta(days=i)).isoformat() for i in range(DAYS)], "zones": zones,
            "events": EVENTS, "sources": {"day_ahead": src, "latest_run": src}}


@pytest.fixture
def replay_store(monkeypatch):
    store = replay_mod.ReplayStore(synthetic_payload())
    monkeypatch.setattr(replay_mod, "get_replay", lambda: store)
    replay_mod._segment_index.cache_clear()
    yield store
    replay_mod._segment_index.cache_clear()


def day(i: int) -> str:
    return (START + timedelta(days=i)).isoformat()


def test_flood_day_shows_alert_fired_day_before(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": "2026-04-27"}).json()
    assert r["verdict"] == "hit"
    assert r["event"]["event_id"] == "E1" and r["event"]["relation"] == "during"
    assert r["event"]["detected"] and r["event"]["alerted_day_before"]
    assert r["event"]["first_alert_date"] == "2026-04-26"
    assert r["previous_day_alert"] is True
    wet = r["zones"][WET_ZONE]
    assert wet["rain_area_24h_mm"] == 60.0 and wet["rain_band"] == 2
    assert r["levels"][wet["zone_id"]] == {"H": MATRIX["High"][2], "M": MATRIX["Medium"][2],
                                           "L": MATRIX["Low"][2], "U": MATRIX["Medium"][2]}


def test_day_before_flood_is_labelled(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": "2026-04-26"}).json()
    assert r["verdict"] == "hit" and r["event"]["relation"] == "day_before"


def test_alert_without_documented_flood_is_flagged_not_hidden(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": day(0)}).json()
    assert r["verdict"] == "no_documented_flood" and r["event"] is None
    assert "incomplete" in r["verdict_text"]


def test_missed_flood_is_reported_as_a_miss(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": "2026-04-23"}).json()
    assert r["verdict"] == "miss" and r["event"]["detected"] is False


def test_dry_day_is_quiet_and_low_roads_stay_none(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": day(9)}).json()
    assert r["verdict"] == "quiet" and r["city_band"] == 0
    assert r["segments_by_level"]["watch"] == r["segments_by_level"]["warning"] == 0


def test_saturated_ground_raises_band_with_live_rule(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": day(SATURATED_DAY)}).json()
    wet = r["zones"][WET_ZONE]
    assert wet["rain_band"] == 1 and "ground_saturated" in wet["reasons"]


def test_missing_zone_is_unknown_never_none(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": day(UNAVAILABLE_DAY)}).json()
    zone = r["zones"][0]
    assert zone["available"] is False and r["levels"][zone["zone_id"]] is None
    assert r["segments_by_level"]["unknown"] > 0


def test_segment_counts_cover_every_road(api, replay_store):
    r = api.get("/api/v1/replay", params={"date": "2026-04-27"}).json()
    assert sum(r["segments_by_level"].values()) == len(get_store().zone)
    assert r["segments_by_level"]["severe"] > 0           # High roads under 50 mm+ are Severe


def test_timeline_scores_events_and_episodes(api, replay_store):
    t = api.get("/api/v1/replay/timeline").json()
    assert t["start"] == day(0) and len(t["bands"]) == DAYS
    assert t["summary"]["events_detected"] == 1 and t["summary"]["events_total"] == 2
    by_start = {e["start"]: e for e in t["episodes"]}
    assert by_start[day(0)]["event_id"] is None             # the unmatched alert
    assert by_start["2026-04-26"]["event_id"] == "E1" and by_start["2026-04-26"]["peak_band"] == 2


def test_out_of_range_date_is_rejected(api, replay_store):
    assert api.get("/api/v1/replay", params={"date": "2020-01-01"}).status_code == 422
    assert api.get("/api/v1/replay", params={"date": day(1), "source": "era5"}).status_code == 422


def test_missing_asset_is_503(api, monkeypatch, tmp_path):
    monkeypatch.setattr(get_settings(), "assets_dir", tmp_path)
    replay_mod.get_replay.cache_clear()
    try:
        assert api.get("/api/v1/replay/timeline").status_code == 503
    finally:
        replay_mod.get_replay.cache_clear()


REAL_ASSET = get_settings().assets_dir / "replay_daily.json.gz"


@pytest.mark.skipif(not REAL_ASSET.exists(), reason="replay asset not built")
def test_real_asset_matches_live_rules_and_validation():
    """Stored bands must equal the live rules, and day-ahead replay must reproduce VALIDATION.md's 6 of 7."""
    replay_mod.get_replay.cache_clear()
    payload = json.loads(gzip.decompress(REAL_ASSET.read_bytes()))
    store = replay_mod.ReplayStore(payload)
    for source in store.sources:
        for i in range(len(store.dates)):
            rows = store.zone_rows(source, store.dates[i])
            stored = [col[i] for col in store.sources[source]["band"]]
            assert [r["rain_band"] if r["available"] else None for r in rows] == stored
    detected = [e["event_id"] for e in store.event_summaries("day_ahead") if e["detected"]]
    assert len(detected) == 6 and "E5" not in detected
