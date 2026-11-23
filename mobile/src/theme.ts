import type { LevelName } from './api';

export const colors = {
  bg: '#F4F6F8',
  card: '#FFFFFF',
  text: '#14202B',
  muted: '#5B6B79',
  border: '#DCE2E8',
  primary: '#0B5FA5',
  danger: '#B3261E',
};

/** Alert level styling. "unknown" is grey, never green: missing data must not read as safe. */
export const levelStyle: Record<LevelName, { bg: string; fg: string; title: string; advice: string }> = {
  none: {
    bg: '#E3EEF7', fg: '#0B3D66', title: 'No alert',
    advice: 'No heavy rain is forecast near here in the next 24 hours. Conditions can change, so check again later.',
  },
  watch: {
    bg: '#FFF4CC', fg: '#6B4E00', title: 'Watch',
    advice: 'Moderate rain is forecast nearby. Low spots on this road may hold water. Plan ahead.',
  },
  warning: {
    bg: '#FFE0C2', fg: '#7A3300', title: 'Warning',
    advice: 'Heavy rain is forecast nearby. Flooding on this road is possible. Avoid travel during the rain if you can.',
  },
  severe: {
    bg: '#F9D3D0', fg: '#7D140E', title: 'Severe',
    advice: 'Very heavy rain is forecast. Flooding is likely. Avoid this road and never cross moving water.',
  },
  unknown: {
    bg: '#E6E8EB', fg: '#3A4550', title: 'Unknown',
    advice: 'FloodGuard does not have fresh forecast data right now. This is not an all-clear. Follow Kenya Met updates.',
  },
};

export const LEVEL_BY_NUMBER: LevelName[] = ['none', 'watch', 'warning', 'severe'];

export const levelName = (level: number | null | undefined): LevelName =>
  level == null ? 'unknown' : LEVEL_BY_NUMBER[level] ?? 'unknown';
