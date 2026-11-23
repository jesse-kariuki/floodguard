import { router } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { api } from '@/api';
import { Card, Choice, DataNotice, ErrorBox, H2, Input, LevelBadge, Loading, Muted, Screen } from '@/components/ui';
import { useAsync, useRoadSearch } from '@/hooks';
import { colors, levelName } from '@/theme';

const openRoad = (r: { road_name: string; latitude: number; longitude: number }) =>
  router.push({ pathname: '/place', params: { latitude: r.latitude, longitude: r.longitude, name: r.road_name } });

export default function Roads() {
  const [minLevel, setMinLevel] = useState(1);
  const [query, setQuery] = useState('');
  const [openError, setOpenError] = useState<string | null>(null);
  const { results, error: searchError } = useRoadSearch(query);
  const atRisk = useAsync(useCallback(() => api.roadsAtRisk(minLevel), [minLevel]));
  const searching = query.trim().length >= 2;

  /** At-risk rows carry no coordinates, so look the road up by name to open it. */
  async function openByName(name: string) {
    try {
      const { results: found } = await api.searchRoads(name);
      const match = found.find((r) => r.road_name === name) ?? found[0];
      if (match) openRoad(match);
    } catch (e) {
      setOpenError((e as Error).message);
    }
  }

  return (
    <Screen refreshing={atRisk.loading && !!atRisk.data} onRefresh={atRisk.reload}>
      <Input label="Search for a road" value={query} onChangeText={setQuery} placeholder="e.g. Outering Road"
        autoCorrect={false} returnKeyType="search" clearButtonMode="while-editing" />
      {(searchError ?? openError) && <ErrorBox message={(searchError ?? openError)!} />}

      {searching ? (
        <Card>
          {!results && !searchError && <Loading />}
          {results?.length === 0 && <Muted>No named road matches “{query.trim()}”.</Muted>}
          {results?.map((r) => (
            <Pressable key={r.road_name} accessibilityRole="button" style={styles.row} onPress={() => openRoad(r)}>
              <Text style={styles.name}>{r.road_name}</Text>
              {r.high_susceptibility_segments > 0 && (
                <Muted>{r.high_susceptibility_segments} high-susceptibility section{r.high_susceptibility_segments > 1 ? 's' : ''}</Muted>
              )}
            </Pressable>
          ))}
        </Card>
      ) : (
        <>
          <H2>Roads under alert now</H2>
          <Choice label="Show" value={minLevel} onChange={setMinLevel}
            options={[{ value: 1, label: 'Watch and above' }, { value: 2, label: 'Warning and above' }, { value: 3, label: 'Severe' }]} />
          {atRisk.error && <ErrorBox message={atRisk.error} onRetry={atRisk.reload} />}
          {!atRisk.data && atRisk.loading && <Loading />}
          {atRisk.data && (
            <>
              <DataNotice data={atRisk.data.data} />
              {atRisk.data.data.state === 'fresh' && (
                <Card>
                  {atRisk.data.roads.length === 0 && <Muted>No named roads are at this level right now.</Muted>}
                  {atRisk.data.roads.map((r) => (
                    <Pressable key={r.road_name} accessibilityRole="button" style={styles.row} onPress={() => openByName(r.road_name)}>
                      <View style={styles.line}>
                        <Text style={styles.name}>{r.road_name}</Text>
                        <LevelBadge level={levelName(r.level)} />
                      </View>
                    </Pressable>
                  ))}
                </Card>
              )}
            </>
          )}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: colors.border, gap: 2 },
  line: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  name: { flex: 1, fontSize: 15, color: colors.text },
});
