import { useCallback } from 'react';

import { api } from '@/api';
import { RiskView } from '@/components/RiskView';
import { ErrorBox, Loading, Screen } from '@/components/ui';
import { currentPosition, useAsync } from '@/hooks';

export default function NearMe() {
  const { data, error, loading, reload } = useAsync(useCallback(async () => {
    const coords = await currentPosition();
    return api.risk(coords.latitude, coords.longitude);
  }, []));

  return (
    <Screen refreshing={loading && !!data} onRefresh={reload}>
      {error && <ErrorBox message={error} onRetry={reload} />}
      {!data && loading && <Loading label="Finding your location…" />}
      {data && <RiskView risk={data} placeLabel="Your location" />}
    </Screen>
  );
}
