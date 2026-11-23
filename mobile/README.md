# FloodGuard mobile

Expo (React Native) app for the FloodGuard backend. It shows flood risk at your location or on any Nairobi road, lists
roads under alert and official Kenya Met warnings, and signs you up for SMS alerts.

## Run it

Start the backend so a phone can reach it (from `backend/`):

```powershell
.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Then, from `mobile/`:

```powershell
npm install
copy .env.example .env      # set EXPO_PUBLIC_API_URL to your PC's LAN IP, or 10.0.2.2 for the Android emulator
npx expo start
```

Scan the QR code with Expo Go, or press `a` for an Android emulator. With the backend's default `SMS_PROVIDER=console`,
sign-up codes are printed in the backend log instead of being texted.

Checks to run before committing:

```powershell
npm run typecheck
npx expo lint
npx expo-doctor
```

## Screens

| Tab | Shows | Backend |
|---|---|---|
| Near me | Alert level, rain forecast and nearby roads at the phone's location | `GET /api/v1/risk` |
| Roads | Search any named road; list roads at or above a level | `/roads/search`, `/roads/at-risk` |
| Official alerts | Kenya Met CAP warnings (Nairobi first) and GDACS events | `/alerts/official` |
| SMS alerts | Sign up with a code, change place, language or level, view history, delete | `/subscribers/*` |

Tapping a road opens `place.tsx`, which shows the same risk view for that road.

## Safety rules the UI keeps

- Stale or missing data shows a grey **Unknown** level and a banner saying it is not an all-clear. It never shows as
  "No alert".
- Susceptibility tiers use their own outlined labels, not the alert colours, so a High-susceptibility road is never
  shown as if it were under a warning.
- Every risk screen shows the backend's disclaimer.

## Layout

```
src/
  api.ts            typed client; response types mirror backend/app/api/*.py
  session.tsx       subscriber token in the device keystore (expo-secure-store)
  hooks.ts          useAsync, debounced road search, current location
  theme.ts          colours and alert-level wording
  components/       RiskView, PlacePicker, shared UI
  app/              Expo Router routes: (tabs)/ and place.tsx
```

## Before release

- Set `EXPO_PUBLIC_API_URL` to the deployed HTTPS backend. Android blocks plain HTTP in release builds.
- Choose `ios.bundleIdentifier` and `android.package` in `app.json`, and replace the placeholder icons in `assets/`.
- Build with EAS: `npx eas-cli@latest build --platform android`.
- The app UI is English only. SMS can be sent in Kiswahili. That text lives in `backend/app/messages.py`.
