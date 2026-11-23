import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { currentPosition, useRoadSearch } from '@/hooks';
import { colors } from '@/theme';
import { Button, Input, Muted } from './ui';

export interface Place {
  latitude: number;
  longitude: number;
  label: string;
}

/** Choose a place to watch: the phone's location, or a named road. */
export function PlacePicker({ value, onChange }: { value: Place | null; onChange: (place: Place) => void }) {
  const [query, setQuery] = useState('');
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const search = useRoadSearch(query);

  async function useMyLocation() {
    setLocating(true);
    setError(null);
    try {
      const coords = await currentPosition();
      onChange({ ...coords, label: 'My location' });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLocating(false);
    }
  }

  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>Place to watch</Text>
      {value && <Text style={styles.selected}>Selected: {value.label}</Text>}
      <Button title="Use my current location" variant="secondary" busy={locating} onPress={useMyLocation} />
      <Input label="Or search for a road" value={query} onChangeText={setQuery} placeholder="e.g. Mombasa Road"
        autoCorrect={false} returnKeyType="search" />
      {search.results?.map((r) => (
        <Pressable key={r.road_name} accessibilityRole="button" style={styles.result}
          onPress={() => {
            onChange({ latitude: r.latitude, longitude: r.longitude, label: r.road_name.slice(0, 40) });
            setQuery('');
          }}>
          <Text style={styles.resultText}>{r.road_name}</Text>
        </Pressable>
      ))}
      {(error ?? search.error) && <Muted>{error ?? search.error}</Muted>}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 8 },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  selected: { fontSize: 15, color: colors.primary, fontWeight: '600' },
  result: { paddingVertical: 12, paddingHorizontal: 4, borderBottomWidth: 1, borderBottomColor: colors.border },
  resultText: { fontSize: 15, color: colors.text },
});
