/** The subscriber token, kept in the device keystore. Only a verified number ever gets one. */
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

const TOKEN_KEY = 'floodguard.token';

interface Session {
  token: string | null;
  ready: boolean;
  signIn: (token: string) => Promise<void>;
  signOut: () => Promise<void>;
}

// expo-secure-store has no web implementation, so the browser build keeps the token in localStorage.
const store = Platform.OS === 'web'
  ? {
      getItemAsync: async (key: string) => globalThis.localStorage?.getItem(key) ?? null,
      setItemAsync: async (key: string, value: string) => globalThis.localStorage?.setItem(key, value),
      deleteItemAsync: async (key: string) => globalThis.localStorage?.removeItem(key),
    }
  : SecureStore;

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    store.getItemAsync(TOKEN_KEY)
      .then(setToken)
      .catch(() => setToken(null))
      .finally(() => setReady(true));
  }, []);

  const signIn = useCallback(async (value: string) => {
    await store.setItemAsync(TOKEN_KEY, value);
    setToken(value);
  }, []);

  const signOut = useCallback(async () => {
    await store.deleteItemAsync(TOKEN_KEY).catch(() => undefined);
    setToken(null);
  }, []);

  const value = useMemo(() => ({ token, ready, signIn, signOut }), [token, ready, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside SessionProvider');
  return session;
}
