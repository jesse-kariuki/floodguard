import { Stack, useLocalSearchParams } from 'expo-router';
import { useCallback } from 'react';

import { api } from '@/api';
import { RiskView } from '@/components/RiskView';
import { ErrorBox, Loading, Screen } from '@/components/ui';
import { useAsync } from '@/hooks';

export default function PlaceRisk() {
  const params = useLocalSearchParams<{ latitude: string; longitude: string; name?: string }>();
  const latitude = Number(params.latitude);
  const longitude = Number(params.longitude);
  const { data, error, loading, reload } = useAsync(useCallback(() => api.risk(latitude, longitude), [latitude, longitude]));

  return (
    <Screen refreshing={loading && !!data} onRefresh={reload}>
      <Stack.Screen options={{ title: params.name ?? 'Flood risk' }} />
      {error && <ErrorBox message={error} onRetry={reload} />}
      {!data && loading && <Loading />}
      {data && <RiskView risk={data} placeLabel={params.name} />}
    </Screen>
  );
}
