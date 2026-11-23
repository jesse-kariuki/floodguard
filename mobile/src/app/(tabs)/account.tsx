import { useCallback, useState } from 'react';
import { Alert, Platform, StyleSheet, Switch, Text, View } from 'react-native';

import { api, ApiError, type Language, type Subscriber } from '@/api';
import { PlacePicker, type Place } from '@/components/PlacePicker';
import { Button, Card, Choice, Disclaimer, ErrorBox, H2, Input, LevelBadge, Loading, Muted, Row, Screen } from '@/components/ui';
import { formatTime } from '@/format';
import { useAsync } from '@/hooks';
import { useSession } from '@/session';
import { colors, levelName } from '@/theme';

const LANGUAGES: { value: Language; label: string }[] = [{ value: 'en', label: 'English' }, { value: 'sw', label: 'Kiswahili' }];
const LEVELS = [{ value: 1, label: 'Watch' }, { value: 2, label: 'Warning' }, { value: 3, label: 'Severe only' }];

export default function Account() {
  const { token, ready } = useSession();
  if (!ready) return <Loading />;
  return token ? <Manage token={token} /> : <SignUp />;
}

function SignUp() {
  const { signIn } = useSession();
  const [phone, setPhone] = useState('');
  const [place, setPlace] = useState<Place | null>(null);
  const [language, setLanguage] = useState<Language>('en');
  const [minLevel, setMinLevel] = useState(1);
  const [consent, setConsent] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const requestCode = () => run(async () => {
    if (!place) throw new Error('Choose a place to watch.');
    const r = await api.subscribe({
      phone, latitude: place.latitude, longitude: place.longitude, place_label: place.label,
      language, min_level: minLevel, consent,
    });
    setSentTo(r.phone);
    setCode('');
  });

  const verify = () => run(async () => {
    const r = await api.verify(sentTo!, code);
    await signIn(r.token);
  });

  if (sentTo) {
    return (
      <Screen>
        <Card>
          <H2>Enter your code</H2>
          <Muted>We sent a 6-digit code to {sentTo}. It expires in a few minutes.</Muted>
          <Input label="Code" value={code} onChangeText={(t) => setCode(t.replace(/\D/g, '').slice(0, 6))}
            keyboardType="number-pad" textContentType="oneTimeCode" autoComplete="sms-otp" maxLength={6} />
          {error && <ErrorBox message={error} />}
          <Button title="Confirm" busy={busy} disabled={code.length !== 6} onPress={verify} />
          <Button title="Send a new code" variant="secondary" disabled={busy} onPress={requestCode} />
          <Button title="Change details" variant="secondary" disabled={busy} onPress={() => setSentTo(null)} />
        </Card>
      </Screen>
    );
  }

  return (
    <Screen>
      <Card>
        <H2>Get flood alerts by SMS</H2>
        <Muted>
          FloodGuard texts you when heavy rain is forecast near the place you choose, and forwards Kenya Met warnings
          that cover it. Works on any phone once you sign up.
        </Muted>
        <Input label="Kenyan mobile number" value={phone} onChangeText={setPhone} placeholder="0712345678"
          keyboardType="phone-pad" textContentType="telephoneNumber" autoComplete="tel" maxLength={20} />
        <PlacePicker value={place} onChange={setPlace} />
        <Choice label="SMS language" options={LANGUAGES} value={language} onChange={setLanguage} />
        <Choice label="Alert me from" options={LEVELS} value={minLevel} onChange={setMinLevel} />
        <View style={styles.consent}>
          <Switch value={consent} onValueChange={setConsent} accessibilityLabel="Consent to SMS alerts" />
          <Text style={styles.consentText}>
            I agree to receive flood SMS at this number. FloodGuard stores my number and chosen place only to send
            these alerts, and I can delete them at any time.
          </Text>
        </View>
        {error && <ErrorBox message={error} />}
        <Button title="Send me a code" busy={busy} disabled={!consent || !place || phone.trim().length < 9}
          onPress={requestCode} />
      </Card>
      <Disclaimer />
    </Screen>
  );
}

function Manage({ token }: { token: string }) {
  const { signOut } = useSession();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const me = useAsync(useCallback(async () => {
    try {
      return await api.me(token);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) await signOut();   // token revoked, e.g. after replying STOP
      throw e;
    }
  }, [token, signOut]));
  const history = useAsync(useCallback(() => api.myAlerts(token), [token]));

  async function save(changes: Parameters<typeof api.updateMe>[1]) {
    setSaving(true);
    setError(null);
    try {
      await api.updateMe(token, changes);
      me.reload();
      setEditing(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  function confirmDelete() {
    const title = 'Stop SMS alerts?';
    const body = 'This deletes your number, place and alert history from FloodGuard.';
    const remove = async () => {
      try {
        await api.deleteMe(token);
        await signOut();
      } catch (e) {
        setError((e as Error).message);
      }
    };
    // Alert.alert does nothing on react-native-web, so the browser build uses the native confirm dialog.
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.(`${title}\n\n${body}`)) void remove();
      return;
    }
    Alert.alert(title, body, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: remove },
    ]);
  }

  const reload = () => { me.reload(); history.reload(); };
  const sub = me.data;

  return (
    <Screen refreshing={me.loading && !!sub} onRefresh={reload}>
      {me.error && <ErrorBox message={me.error} onRetry={me.reload} />}
      {!sub && me.loading && <Loading />}
      {sub && (editing
        ? <Edit sub={sub} saving={saving} onSave={save} onCancel={() => setEditing(false)} />
        : (
          <Card>
            <H2>Your SMS alerts are on</H2>
            <Row label="Number" value={sub.phone} />
            <Row label="Place" value={sub.place_label ?? `${sub.latitude.toFixed(4)}, ${sub.longitude.toFixed(4)}`} />
            <Row label="Language" value={sub.language === 'sw' ? 'Kiswahili' : 'English'} />
            <Row label="Alert me from" value={LEVELS.find((l) => l.value === sub.min_level)?.label ?? String(sub.min_level)} />
            <Row label="Road susceptibility there" value={sub.local_susceptibility_tier} />
            <Button title="Change settings" variant="secondary" onPress={() => setEditing(true)} />
          </Card>
        ))}
      {error && <ErrorBox message={error} />}

      {sub && (
        <Card>
          <H2>Alerts sent to you</H2>
          {history.error && <Muted>{history.error}</Muted>}
          {history.data?.alerts.length === 0 && <Muted>No alerts yet.</Muted>}
          {history.data?.alerts.map((a, i) => (
            <View key={i} style={styles.alert}>
              <View style={styles.alertHead}>
                <Muted>{formatTime(a.sent_at)}</Muted>
                {a.level != null && <LevelBadge level={levelName(a.level)} />}
              </View>
              <Text style={styles.alertText}>{a.message}</Text>
            </View>
          ))}
        </Card>
      )}

      {sub && <Button title="Stop alerts and delete my data" variant="danger" onPress={confirmDelete} />}
    </Screen>
  );
}

function Edit({ sub, saving, onSave, onCancel }: {
  sub: Subscriber; saving: boolean; onSave: (changes: Parameters<typeof api.updateMe>[1]) => void; onCancel: () => void;
}) {
  const [place, setPlace] = useState<Place | null>(null);
  const [language, setLanguage] = useState<Language>(sub.language);
  const [minLevel, setMinLevel] = useState(sub.min_level);

  return (
    <Card>
      <H2>Change settings</H2>
      <Muted>Current place: {sub.place_label ?? 'pinned location'}</Muted>
      <PlacePicker value={place} onChange={setPlace} />
      <Choice label="SMS language" options={LANGUAGES} value={language} onChange={setLanguage} />
      <Choice label="Alert me from" options={LEVELS} value={minLevel} onChange={setMinLevel} />
      <Button title="Save" busy={saving} onPress={() => onSave({
        language, min_level: minLevel,
        ...(place ? { latitude: place.latitude, longitude: place.longitude, place_label: place.label } : {}),
      })} />
      <Button title="Cancel" variant="secondary" disabled={saving} onPress={onCancel} />
    </Card>
  );
}

const styles = StyleSheet.create({
  consent: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  consentText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 18 },
  alert: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.border, gap: 4 },
  alertHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  alertText: { fontSize: 14, color: colors.text, lineHeight: 20 },
});
