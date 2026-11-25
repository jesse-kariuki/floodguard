# FloodGuard: presentation script (5 minutes)

Fill in the two placeholders in Part 1 before you present.

| Part | Clock | What's on screen |
|---|---|---|
| 1. Introduction | 0:00 – 0:30 | Video paused on the title card (0:00) |
| 2. Features | 0:30 – 1:30 | Video still paused on the title card, or the app's home screen |
| 3. Code explanation | 1:30 – 2:30 | Android Studio, opening the four files below |
| 4. Demonstration | 2:30 – 4:30 | **Play `FloodGuard_demo.mp4`** (exactly 2:00). Lines below are keyed to the video's own timer |
| 5. Conclusion | 4:30 – 5:00 | Video ends on the flood map |

Speaking pace: the word counts are sized for about 2.3 words a second, which is a calm pace. If you're running
behind, skip the lines marked *(optional)*.

---

## 1. Introduction (0:00 – 0:30)

> Good morning. My name is **[YOUR NAME]**, registration number **[REG NUMBER]**.
>
> My project is **FloodGuard**, a native Android app written in Kotlin that shows the flood risk of every road in
> Nairobi. Nairobi floods almost every rainy season, and people usually find out a road is flooded only when they are
> already on it. FloodGuard combines a machine-learning flood map with live rainfall forecasts, so people can check a
> road before they travel and get an SMS when heavy rain is coming.

## 2. Project features (0:30 – 1:30)

> The app has five main features.
>
> **One: the flood map.** All 83,596 road segments in Nairobi are coloured by flood susceptibility, where red is high,
> yellow medium and green low. The colours come from a Random Forest model. I can filter by level, search for any
> road, or jump to my location.
>
> **Two: live risk for any spot.** Tapping a road shows today's rain alert there, the rainfall forecast for the next
> 6 and 24 hours, and the roads nearby. The forecasts refresh every hour from Open-Meteo.
>
> **Three: replay.** I can pick any day since February 2024 and the map recolours to the alerts FloodGuard would have
> shown, using the forecasts as they were issued then. It caught 6 of 7 documented floods.
>
> **Four: an alerts feed** with roads under alert now and official Kenya Met warnings.
>
> **Five: SMS alerts.** Anyone can sign up with a phone number, a place, and English or Kiswahili. It also works on
> phones without the app.
>
> *(optional)* The app never shows missing data as "safe", and the map still works offline.

## 3. Code explanation (1:30 – 2:30)

Open each file in Android Studio as you talk about it (`app/src/main/java/ke/floodguard/app/`).

**`MainActivity.kt`, lines 48–91: the app shell** *(about 15 s)*
> The whole UI is built with Jetpack Compose. In `onCreate` I call `setContent`, and I create one ViewModel per
> screen with a factory that passes in the shared API client. A `Scaffold` holds the bottom navigation bar, and the
> current tab is stored with `rememberSaveable`, so it survives rotation. The map stays composed under the other tabs,
> so switching back is instant.

**`ui/map/MapViewModel.kt`, lines 21–24, then 80–94: state** *(about 15 s)*
> Every screen's state is a sealed interface, `Load`, which is either `Loading`, `Ready` or `Failed`. `loadRoads`
> first shows the copy saved on the phone, then launches a coroutine in `viewModelScope` to fetch a newer one. The
> ViewModel exposes these as `StateFlow`s, so the Compose UI redraws by itself whenever they change.

**`data/ApiClient.kt`, lines 58–81: networking** *(about 15 s)*
> Networking uses OkHttp inside `suspend` functions on `Dispatchers.IO`, so it never blocks the main thread. The road
> map is 17 MB, so I stream it straight to disk and send the saved ETag. If the server answers *304 Not Modified*,
> nothing is downloaded, and the app keeps working offline.

**`ui/map/FloodMap.kt`, lines 118–121, 201–224 and 275: the map** *(about 15 s)*
> MapLibre is a classic Android View, so I wrap it in Compose with `AndroidView`. Instead of one layer with a colour
> expression per road, I build one layer per zone and risk tier with a fixed filter. Recolouring for replay then
> changes only paint properties. That took a redraw from over 30 seconds to a single frame.

## 4. Application demonstration (2:30 – 4:30): press play now

Timestamps are the **video's own timer** (0:00 to 2:00). "▶" lines tell you what's happening on screen.

**0:00 – 0:13 · The flood map**
▶ 0:00 title card · 0:04 app open on the map
> This is FloodGuard running on an Android emulator. It opens on the flood map, with every road in Nairobi coloured by
> susceptibility: red high, yellow medium, green low.

**0:13 – 0:24 · Filters**
▶ 0:13 Low, Medium and No-data chips switched off · 0:20 switched back on
> Using the filter chips, I hide Low, Medium and No data, so only the high-risk roads remain. Tapping them again
> brings everything back.

**0:24 – 0:49 · Search and live risk**
▶ 0:24 typing "Jogoo" · 0:27 Jogoo Road picked, sheet opens · 0:39 sheet scrolled · 0:43 sheet closed
> Next, I search for a road: Jogoo Road. The map flies there and a sheet opens with the live rain alert. Today it says
> *No alert*, and shows the rain forecast for the next 6 and 24 hours. Scrolling down shows the area's susceptibility
> and the roads nearby.

**0:49 – 1:12 · Replay a past day**
▶ 0:49 replay opens on 21 April 2025 · 0:58 panel collapsed, whole city shown · 1:06 next day · 1:10 replay closed
> Now, replay mode. It opens on the 21st of April 2025, when floods killed people in Mathare and Mukuru. The card
> confirms an alert was already showing the day before. With the panel collapsed, the whole city is under a Watch.
> I step to the next day, then leave replay.

**1:12 – 1:19 · Alerts tab**
▶ 1:12 Alerts tab
> The Alerts tab lists roads under an alert right now and official Kenya Met warnings. There are none today.

**1:19 – 1:46 · SMS sign-up**
▶ 1:19 SMS tab · 1:24 number typed, Kirinyaga Road picked, Kiswahili, consent · 1:33 "Send me a code" · 1:37 code entered · 1:42 subscribed
> Finally, SMS alerts. I enter a phone number, pick the road to watch, Kirinyaga Road, choose Kiswahili and give
> consent. I tap *Send me a code*, enter the six-digit code and confirm. I'm now subscribed, and that road is rated
> High.

**1:46 – 2:00 · Back to the map**
▶ 1:46 Flood map tab · 1:51 – 2:00 holds on the map
> And back to the map. It stayed loaded in the background, so switching is instant. That completes the demo.

## 5. Conclusion (4:30 – 5:00)

> To conclude: I built a complete Android app in Kotlin and Jetpack Compose, connected to a Python FastAPI backend.
> It has a live flood map of 83,000 road segments, a replay mode, an alerts feed and SMS sign-up.
>
> The two main challenges were performance, getting 17 megabytes of road data to draw smoothly on a phone, and
> honesty about uncertainty: the model has a small validation sample, so the app never shows missing data as safe.
>
> Next, I'd deploy the backend over HTTPS and test with real users. Thank you.
