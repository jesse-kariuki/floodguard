import { StyleSheet, Text, View } from 'react-native';

import type { RiskResponse } from '@/api';
import { mm, REASON_TEXT } from '@/format';
import { colors, levelStyle } from '@/theme';
import { Card, DataNotice, Disclaimer, H2, Muted, Row, TierBadge } from './ui';

export function RiskView({ risk, placeLabel }: { risk: RiskResponse; placeLabel?: string }) {
  const s = levelStyle[risk.level_name];
  const w = risk.weather;
  return (
    <>
      <DataNotice data={risk.data} />

      <View style={[styles.hero, { backgroundColor: s.bg }]} accessibilityRole="summary">
        {placeLabel && <Text style={[styles.place, { color: s.fg }]}>{placeLabel}</Text>}
        <Text style={[styles.level, { color: s.fg }]}>{s.title}</Text>
        <Text style={[styles.advice, { color: s.fg }]}>{s.advice}</Text>
      </View>

      {w && w.available && (
        <Card>
          <H2>Rain forecast</H2>
          <Row label="Next 6 hours" value={mm(w.rain_next_6h_mm)} />
          <Row label="Next 24 hours" value={mm(w.rain_next_24h_mm)} />
          <Row label="Wettest nearby (24 h)" value={mm(w.rain_area_24h_mm)} />
          <Row label="Past 72 hours" value={mm(w.rain_prev_72h_mm)} />
          {w.precip_probability_max != null && <Row label="Chance of rain" value={`${Math.round(w.precip_probability_max)}%`} />}
          {w.reasons.map((r) => <Muted key={r}>• {REASON_TEXT[r] ?? r}</Muted>)}
        </Card>
      )}

      <Card>
        <H2>This location</H2>
        <Row label="Road flood susceptibility" value={risk.local_susceptibility_tier} />
        <Row label="Distance to nearest road" value={`${Math.round(risk.distance_to_road_m)} m`} />
        <Muted>
          Susceptibility comes from a model of where water collects on roads. It can raise an alert but never
          silences one, because many real flood sites score Low.
        </Muted>
      </Card>

      {risk.nearby_roads.length > 0 && (
        <Card>
          <H2>Roads nearby</H2>
          {risk.nearby_roads.slice(0, 8).map((r) => (
            <View key={r.road_name} style={styles.road}>
              <Text style={styles.roadName}>{r.road_name}</Text>
              <TierBadge tier={r.tier} />
            </View>
          ))}
          <Muted>Labels show each road’s flood susceptibility, not the current alert.</Muted>
        </Card>
      )}

      <Disclaimer text={risk.disclaimer} />
    </>
  );
}

const styles = StyleSheet.create({
  hero: { borderRadius: 16, padding: 20, gap: 6 },
  place: { fontSize: 14, fontWeight: '600', opacity: 0.85 },
  level: { fontSize: 34, fontWeight: '800' },
  advice: { fontSize: 15, lineHeight: 21 },
  road: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, paddingVertical: 4 },
  roadName: { flex: 1, fontSize: 15, color: colors.text },
});
