import { Linking, StyleSheet, Text } from 'react-native';

import { api } from '@/api';
import { Card, ErrorBox, H2, Loading, Muted, Screen } from '@/components/ui';
import { formatTime } from '@/format';
import { useAsync } from '@/hooks';
import { colors } from '@/theme';

export default function OfficialAlerts() {
  const { data, error, loading, reload } = useAsync(api.officialAlerts);
  const kmd = data ? [...data.kmd_alerts].sort((a, b) => Number(b.covers_nairobi) - Number(a.covers_nairobi)) : [];

  return (
    <Screen refreshing={loading && !!data} onRefresh={reload}>
      <Muted>Warnings issued by the Kenya Meteorological Department. FloodGuard forwards these unchanged.</Muted>
      {error && <ErrorBox message={error} onRetry={reload} />}
      {!data && loading && <Loading />}

      {data && kmd.length === 0 && <Card><Muted>No active Kenya Met warnings.</Muted></Card>}
      {kmd.map((a) => (
        <Card key={a.identifier} style={a.covers_nairobi ? styles.nairobi : undefined}>
          {a.covers_nairobi && <Text style={styles.tag}>Covers Nairobi</Text>}
          <H2>{a.headline ?? a.event ?? 'Kenya Met alert'}</H2>
          {a.severity && <Muted>Severity: {a.severity}</Muted>}
          <Muted>Issued {formatTime(a.sent_at)} · expires {formatTime(a.expires_at)}</Muted>
          {a.description && <Text style={styles.body}>{a.description}</Text>}
          {a.instruction && <Text style={[styles.body, styles.instruction]}>{a.instruction}</Text>}
          {a.area && <Muted>Area: {a.area}</Muted>}
          {a.url && <Text style={styles.link} onPress={() => Linking.openURL(a.url!)}>Open on meteo.go.ke</Text>}
        </Card>
      ))}

      {data && data.gdacs_events.length > 0 && (
        <>
          <H2>Recent flood events in Kenya (GDACS)</H2>
          {data.gdacs_events.map((e, i) => (
            <Card key={e.event_id ?? i}>
              <Text style={styles.body}>{e.name ?? 'Flood event'}</Text>
              <Muted>{e.alert_level ? `${e.alert_level} alert · ` : ''}{formatTime(e.from)} to {formatTime(e.to)}</Muted>
              {e.url && <Text style={styles.link} onPress={() => Linking.openURL(e.url!)}>GDACS report</Text>}
            </Card>
          ))}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  nairobi: { borderColor: colors.primary, borderWidth: 2 },
  tag: { color: colors.primary, fontWeight: '700', fontSize: 12, textTransform: 'uppercase' },
  body: { fontSize: 15, color: colors.text, lineHeight: 21 },
  instruction: { fontWeight: '600' },
  link: { color: colors.primary, fontSize: 15, fontWeight: '600', paddingVertical: 4 },
});
