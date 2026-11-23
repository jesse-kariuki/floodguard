import * as Location from 'expo-location';
import { useCallback, useEffect, useState } from 'react';

import { api, ApiError, type RoadSearchResult } from './api';

/** Runs `load` on mount, whenever `load` changes, and on `reload()`. Keeps the last good data while refreshing.
 *  Wrap `load` in useCallback so it only changes when its inputs do. */
export function useAsync<T>(load: () => Promise<T>) {
  const [nonce, setNonce] = useState(0);
  const [settled, setSettled] = useState<{ load: unknown; nonce: number; data: T | null; error: string | null }>(
    { load: null, nonce: -1, data: null, error: null });

  useEffect(() => {
    let cancelled = false;
    load().then(
      (data) => !cancelled && setSettled({ load, nonce, data, error: null }),
      (e) => !cancelled && setSettled((s) => ({ ...s, load, nonce, error: e instanceof Error ? e.message : String(e) })),
    );
    return () => {
      cancelled = true;
    };
  }, [load, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  const loading = settled.load !== load || settled.nonce !== nonce;
  return { data: settled.data, error: settled.error, loading, reload };
}

/** Debounced road-name search. `results` is null until the query has at least 2 characters. */
export function useRoadSearch(query: string) {
  const q = query.trim();
  const [found, setFound] = useState<{ q: string; results: RoadSearchResult[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (q.length < 2) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      api.searchRoads(q)
        .then((r) => {
          if (!cancelled) {
            setFound({ q, results: r.results });
            setError(null);
          }
        })
        .catch((e: Error) => !cancelled && setError(e.message));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [q]);

  return { results: q.length >= 2 && found?.q === q ? found.results : null, error: q.length >= 2 ? error : null };
}

export interface Coords {
  latitude: number;
  longitude: number;
}

export async function currentPosition(): Promise<Coords> {
  const { status } = await Location.requestForegroundPermissionsAsync();
  if (status !== 'granted') {
    throw new ApiError('Location permission is off. Allow it in Settings, or search for a road instead.', 0);
  }
  const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
  return { latitude: position.coords.latitude, longitude: position.coords.longitude };
}
