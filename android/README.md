# FloodGuard Android

Native Android app (Kotlin, Jetpack Compose) for the FloodGuard backend. It opens on a flood map of Nairobi, with
every road coloured by flood susceptibility: red is High, yellow is Medium, green is Low and grey is no data. Tap any
road to see today's live rain alert there. It lives alongside the Expo app in `mobile/`; both use the same API.

## Run it

1. Start the backend so the emulator or phone can reach it (from `backend/`):

   ```powershell
   .venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

2. Open this `android/` folder in Android Studio (2025.1 or newer) and press Run. Or, from the command line:

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew installDebug
   ```

The backend URL is `floodguardApiUrl` in `gradle.properties`. The default `http://10.0.2.2:8000` is your PC as seen
from the Android emulator. For a real phone on the same Wi-Fi, use your PC's LAN IP:

```powershell
.\gradlew installDebug -PfloodguardApiUrl=http://192.168.1.20:8000
```

With the backend's default `SMS_PROVIDER=console`, sign-up codes appear in the backend log instead of by SMS.

Tests: `.\gradlew testDebugUnitTest`.

## Screens

| Tab | Shows | Backend |
|---|---|---|
| Flood map | Street map with every road coloured by susceptibility, tier filters, road search, my location, and a sheet with the live alert, rain forecast and nearby roads for the tapped spot | `/map/roads`, `/zones`, `/risk`, `/roads/search` |
| Alerts | Roads under a rain alert now, Kenya Met warnings (or a notice when the feed is down), GDACS events | `/roads/at-risk`, `/alerts/official`, `/status` |
| SMS alerts | Sign up with a code, change language or level, alert history, delete | `/subscribers/*` |

## How the map works

- The basemap is [OpenFreeMap](https://openfreemap.org) vector tiles (OpenStreetMap data) drawn by
  [MapLibre](https://maplibre.org). Neither needs an API key or a billing account. To use another style, change
  `BASEMAP_STYLE` in `ui/map/FloodMap.kt`.
- The road colours come from `GET /api/v1/map/roads`: 83,596 segments, 2.3 MB gzipped. The app streams the file to
  disk and MapLibre reads it natively. After that, it revalidates with an ETag, so a later launch downloads nothing
  unless the model was retrained, and the map still works offline from the saved copy.
- Susceptibility is static: it shows where water tends to collect, not whether it is raining. The legend says so, and
  the live alert for a spot is shown separately when you tap it. The banner under the search bar summarises today's
  forecast.

## Safety rules the UI keeps

- Stale or missing data shows a grey **Unknown** level and says it is not an all-clear. It never shows as "No alert".
- If the backend could not read the Kenya Met feed, the Alerts tab says warnings may be missing rather than "none".
- Every risk view shows the backend's disclaimer.

## Layout

```
app/src/main/java/ke/floodguard/app/
  FloodGuardApp.kt      app singletons: API client, token store, locator
  MainActivity.kt       bottom navigation; the map stays loaded under the other tabs
  data/                 API client and models, Keystore-encrypted token, one-shot location
  ui/map/               FloodMap (MapLibre view and layers), MapScreen (search, chips, legend, sheet), MapViewModel
  ui/alerts/            Alerts tab
  ui/sms/               SMS sign-up and account
  ui/common/, ui/theme/ shared components, colours and alert wording
```

## Before release

- Point `floodguardApiUrl` at the deployed **HTTPS** backend. Release builds block plain HTTP; only debug builds allow
  it (`app/src/debug/`).
- Choose the final `applicationId` (now `ke.floodguard.app`) and replace the placeholder icon.
- Create a signing key and add a `signingConfig` for release.
- The full road map takes several seconds to parse on low-end phones. If that becomes a problem, serve it as vector
  tiles (for example PMTiles built with tippecanoe) instead of one GeoJSON file.
- OpenFreeMap is free and has no usage limits, but for a public launch consider hosting your own tiles or a paid
  provider with an SLA.
