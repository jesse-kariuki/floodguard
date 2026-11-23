/** Typed client for the FloodGuard backend. Shapes mirror backend/app/api/*.py. */

export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8000').replace(/\/$/, '');

const TIMEOUT_MS = 15000;

export type LevelName = 'none' | 'watch' | 'warning' | 'severe' | 'unknown';
export type Tier = 'Low' | 'Medium' | 'High';
export type Language = 'en' | 'sw';

export interface DataStatus {
  state: 'fresh' | 'stale' | 'no_data';
  issue_time: string | null;
  age_minutes: number | null;
  run_status?: string;
}

export interface ZoneWeather {
  zone_id: string;
  latitude: number | null;
  longitude: number | null;
  available: boolean;
  rain_band: number;
  rain_band_name: string;
  rain_next_6h_mm: number | null;
  rain_next_24h_mm: number | null;
  rain_area_24h_mm: number | null;
  rain_prev_72h_mm: number | null;
  max_hourly_mm: number | null;
  precip_probability_max: number | null;
  river_discharge_m3s: number | null;
  river_p99_m3s: number | null;
  reasons: string[];
}

export interface NearbyRoad {
  road_name: string;
  susceptibility: number;
  tier: Tier;
}

export interface RiskResponse {
  data: DataStatus;
  zone_id: string;
  level: number | null;
  level_name: LevelName;
  local_susceptibility: number | null;
  local_susceptibility_tier: Tier;
  distance_to_road_m: number;
  nearby_roads: NearbyRoad[];
  weather: ZoneWeather | null;
  disclaimer: string;
}

export interface RoadSearchResult {
  road_name: string;
  latitude: number;
  longitude: number;
  segments: number;
  high_susceptibility_segments: number;
}

export interface RoadAtRisk {
  road_name: string;
  level: number;
  segments: number;
  zones: string[];
}

export interface KmdAlert {
  identifier: string;
  event: string | null;
  headline: string | null;
  description: string | null;
  instruction: string | null;
  severity: string | null;
  area: string | null;
  sent_at: string | null;
  expires_at: string | null;
  url: string | null;
  covers_nairobi: boolean;
}

export interface GdacsEvent {
  event_id: number | null;
  alert_level: string | null;
  name: string | null;
  from: string | null;
  to: string | null;
  is_current: string | boolean | null;
  url: string | null;
}

export interface Subscriber {
  phone: string;
  status: string;
  latitude: number;
  longitude: number;
  place_label: string | null;
  zone_id: string;
  local_susceptibility_tier: Tier;
  language: Language;
  min_level: number;
  last_alert_level: number | null;
  last_alert_at: string | null;
}

export interface SentAlert {
  kind: string;
  level: number | null;
  message: string;
  sent_at: string;
}

export interface SubscribeBody {
  phone: string;
  latitude: number;
  longitude: number;
  place_label?: string | null;
  language: Language;
  min_level: number;
  consent: boolean;
}

export type SubscriberUpdate = Partial<Pick<SubscribeBody, 'latitude' | 'longitude' | 'place_label' | 'language' | 'min_level'>>;

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/** FastAPI sends `detail` as a string for HTTPException and as a list for validation errors. */
function detailMessage(body: unknown, status: number): string {
  const detail = (body as { detail?: unknown } | null)?.detail;
  if (typeof detail === 'string') return detail;
  if (Array.isArray(detail) && detail.length) {
    const first = detail[0] as { msg?: string; loc?: unknown[] };
    const field = first.loc?.[first.loc.length - 1];
    return field ? `${String(field)}: ${first.msg}` : first.msg ?? 'Invalid request.';
  }
  return status >= 500 ? 'The FloodGuard server had a problem. Try again shortly.' : `Request failed (${status}).`;
}

async function request<T>(path: string, init: RequestInit & { token?: string | null } = {}): Promise<T> {
  const { token, headers, ...rest } = init;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...rest,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(rest.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
    });
  } catch {
    throw new ApiError('Could not reach FloodGuard. Check your connection.', 0);
  } finally {
    clearTimeout(timer);
  }
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(detailMessage(body, response.status), response.status);
  return body as T;
}

const qs = (params: Record<string, string | number | boolean>) =>
  Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&');

export const api = {
  risk: (latitude: number, longitude: number) =>
    request<RiskResponse>(`/api/v1/risk?${qs({ latitude, longitude })}`),
  searchRoads: (q: string) =>
    request<{ results: RoadSearchResult[] }>(`/api/v1/roads/search?${qs({ q, limit: 15 })}`),
  roadsAtRisk: (minLevel = 1) =>
    request<{ data: DataStatus; roads: RoadAtRisk[] }>(`/api/v1/roads/at-risk?${qs({ min_level: minLevel, limit: 100 })}`),
  officialAlerts: () =>
    request<{ kmd_alerts: KmdAlert[]; gdacs_events: GdacsEvent[] }>('/api/v1/alerts/official'),
  subscribe: (body: SubscribeBody) =>
    request<{ status: string; phone: string; expires_in_seconds: number }>('/api/v1/subscribers', {
      method: 'POST', body: JSON.stringify(body),
    }),
  verify: (phone: string, code: string) =>
    request<{ token: string; subscriber: Subscriber }>('/api/v1/subscribers/verify', {
      method: 'POST', body: JSON.stringify({ phone, code }),
    }),
  me: (token: string) => request<Subscriber>('/api/v1/subscribers/me', { token }),
  updateMe: (token: string, changes: SubscriberUpdate) =>
    request<Subscriber>('/api/v1/subscribers/me', { method: 'PATCH', token, body: JSON.stringify(changes) }),
  myAlerts: (token: string) => request<{ alerts: SentAlert[] }>('/api/v1/subscribers/me/alerts', { token }),
  deleteMe: (token: string) => request<void>('/api/v1/subscribers/me', { method: 'DELETE', token }),
};
