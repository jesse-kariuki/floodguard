import type { ReactNode } from 'react';
import {
  ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View,
  type TextInputProps, type ViewStyle,
} from 'react-native';

import type { DataStatus, LevelName, Tier } from '@/api';
import { formatAge, formatTime } from '@/format';
import { colors, levelStyle } from '@/theme';

export function Screen({ children, refreshing, onRefresh }: {
  children: ReactNode; refreshing?: boolean; onRefresh?: () => void;
}) {
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      refreshControl={onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} /> : undefined}
    >
      {children}
    </ScrollView>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function H2({ children }: { children: ReactNode }) {
  return <Text style={styles.h2}>{children}</Text>;
}

export function Muted({ children }: { children: ReactNode }) {
  return <Text style={styles.muted}>{children}</Text>;
}

export function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

export function Button({ title, onPress, disabled, busy, variant = 'primary' }: {
  title: string; onPress: () => void; disabled?: boolean; busy?: boolean; variant?: 'primary' | 'secondary' | 'danger';
}) {
  const inactive = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive }}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [styles.button, styles[variant], inactive && styles.disabled, pressed && styles.pressed]}
    >
      {busy ? <ActivityIndicator color={variant === 'secondary' ? colors.primary : '#fff'} />
        : <Text style={[styles.buttonText, variant === 'secondary' && styles.secondaryText]}>{title}</Text>}
    </Pressable>
  );
}

export function Input(props: TextInputProps & { label: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{props.label}</Text>
      <TextInput placeholderTextColor={colors.muted} {...props} style={[styles.input, props.style]} />
    </View>
  );
}

export function Choice<T extends string | number>({ label, options, value, onChange }: {
  label: string; options: { value: T; label: string }[]; value: T; onChange: (value: T) => void;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <View style={styles.choices}>
        {options.map((o) => {
          const selected = o.value === value;
          return (
            <Pressable
              key={String(o.value)}
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              onPress={() => onChange(o.value)}
              style={[styles.choice, selected && styles.choiceSelected]}
            >
              <Text style={[styles.choiceText, selected && styles.choiceTextSelected]}>{o.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Card style={styles.error}>
      <Text style={styles.errorText}>{message}</Text>
      {onRetry && <Button title="Try again" variant="secondary" onPress={onRetry} />}
    </Card>
  );
}

export function Loading({ label }: { label?: string }) {
  return (
    <View style={styles.loading}>
      <ActivityIndicator color={colors.primary} />
      {label && <Muted>{label}</Muted>}
    </View>
  );
}

/** Shown whenever data is not fresh, so a stale or missing level is never mistaken for "no risk". */
export function DataNotice({ data }: { data: DataStatus }) {
  if (data.state === 'fresh') {
    return <Muted>Forecast updated {formatAge(data.age_minutes)} · issued {formatTime(data.issue_time)}</Muted>;
  }
  const text = data.state === 'no_data'
    ? 'FloodGuard has no forecast data yet. Risk levels are unknown, not safe.'
    : `Forecast data is out of date (last update ${formatAge(data.age_minutes)}). Risk levels are unknown, not safe.`;
  return (
    <Card style={styles.notice}>
      <Text style={styles.noticeText}>{text}</Text>
    </Card>
  );
}

export function LevelBadge({ level }: { level: LevelName }) {
  const s = levelStyle[level];
  return (
    <View style={[styles.badge, { backgroundColor: s.bg }]}>
      <Text style={[styles.badgeText, { color: s.fg }]}>{s.title}</Text>
    </View>
  );
}

const TIER_COLORS: Record<Tier, string> = { Low: '#5B6B79', Medium: '#8A5A00', High: '#9C2A1F' };

export function TierBadge({ tier }: { tier: Tier }) {
  return (
    <View style={[styles.badge, styles.tierBadge, { borderColor: TIER_COLORS[tier] }]}>
      <Text style={[styles.badgeText, { color: TIER_COLORS[tier] }]}>{tier}</Text>
    </View>
  );
}

export function Disclaimer({ text }: { text?: string }) {
  return (
    <Text style={styles.disclaimer}>
      {text ?? 'Decision support only, not an official warning. Follow Kenya Met and county instructions, and never cross moving water.'}
    </Text>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 16, gap: 12, paddingBottom: 32 },
  card: { backgroundColor: colors.card, borderRadius: 12, padding: 16, gap: 8, borderWidth: 1, borderColor: colors.border },
  h2: { fontSize: 17, fontWeight: '700', color: colors.text },
  muted: { fontSize: 13, color: colors.muted, lineHeight: 18 },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: 12, paddingVertical: 2 },
  rowLabel: { color: colors.muted, fontSize: 14, flexShrink: 1 },
  rowValue: { color: colors.text, fontSize: 14, fontWeight: '600' },
  button: { minHeight: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
  primary: { backgroundColor: colors.primary },
  secondary: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.primary },
  danger: { backgroundColor: colors.danger },
  disabled: { opacity: 0.5 },
  pressed: { opacity: 0.8 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  secondaryText: { color: colors.primary },
  field: { gap: 6 },
  fieldLabel: { fontSize: 14, fontWeight: '600', color: colors.text },
  input: {
    minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, paddingHorizontal: 12,
    fontSize: 16, color: colors.text, backgroundColor: colors.card,
  },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: {
    minHeight: 40, paddingHorizontal: 14, borderRadius: 20, borderWidth: 1, borderColor: colors.border,
    justifyContent: 'center', backgroundColor: colors.card,
  },
  choiceSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  choiceText: { color: colors.text, fontSize: 14 },
  choiceTextSelected: { color: '#fff', fontWeight: '600' },
  error: { borderColor: '#F0B8B3', backgroundColor: '#FDF1F0' },
  errorText: { color: colors.danger, fontSize: 14, lineHeight: 20 },
  loading: { padding: 32, alignItems: 'center', gap: 8 },
  notice: { backgroundColor: '#FFF4CC', borderColor: '#E8D48A' },
  noticeText: { color: '#6B4E00', fontSize: 14, lineHeight: 20, fontWeight: '600' },
  badge: { alignSelf: 'flex-start', borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3 },
  tierBadge: { borderWidth: 1, backgroundColor: 'transparent' },
  badgeText: { fontSize: 12, fontWeight: '700', textTransform: 'uppercase' },
  disclaimer: { fontSize: 12, color: colors.muted, lineHeight: 17, marginTop: 4 },
});
