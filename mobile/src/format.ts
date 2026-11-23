export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('en-KE', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function formatAge(minutes: number | null | undefined): string {
  if (minutes == null) return 'unknown';
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${Math.round(minutes)} min ago`;
  const hours = minutes / 60;
  return hours < 48 ? `${Math.round(hours)} h ago` : `${Math.round(hours / 24)} days ago`;
}

export const mm = (value: number | null | undefined) => (value == null ? '—' : `${value.toFixed(1)} mm`);

export const REASON_TEXT: Record<string, string> = {
  ground_saturated: 'Ground is already saturated from recent rain',
  river_above_p99: 'River flow is unusually high',
  rain_nearby: 'Heavier rain is forecast in a neighbouring area',
};
