# Spring Up — Visual Asset Kit

A coordinated visual package for the Spring Up Android alarm app, using its existing signature red (`#E60012`) with warm sunrise peach/cream and amber Watchdog accents.

## Contents

- **Brand & launcher:** adaptive launcher XMLs, density-specific legacy launcher PNGs, launcher foreground vector and a brand-mark SVG/PNG.
- **Mission icon family:** Math, Shake, Watchdog, Streak, Success, enabled alarm, disabled alarm. Includes SVG sources, 256px PNG exports and Android VectorDrawable XMLs.
- **Empty state:** `spring_up_empty_state.png`, a transparent sunrise alarm illustration for the `No Alarms Configured` screen.
- **Mission illustration art:** Math, Shake and Watchdog illustration panels under `onboarding/` plus nodpi Android copies.
- **Animations:** `pulse_stage.xml`, `mission_success_pop.xml`, `mission_success_settle.xml`, and a property animator resource.
- **Surfaces/colors:** sunrise gradient, neutral cards, mission badges, progress track/fill, new day/night color tokens.
- **Store & onboarding:** Google Play feature graphic (1024×500), three designed onboarding panels, and three Google Play screenshot concept mockups (1080×1920).
- **Previews & source:** contact sheets and the original generated source artwork.

## Install the Android resources

Copy the contents of `android-resources/app/src/main/res/` into your app's existing `app/src/main/res/` folder, merging folders. Do **not** replace your existing `colors.xml`; `spring_up_colors.xml` and `values-night/spring_up_colors.xml` are additive resources with `springup_`-prefixed names.

The Android manifest in this project already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, so the included launcher icons use the existing resource names. The adaptive icon XMLs are under `mipmap-anydpi-v26/`; legacy PNGs are supplied for older Android versions.

### Wiring in the UI

- Main empty state: set the empty-state `ImageView` source to `@drawable/spring_up_empty_state` (or use `android:src`/ViewBinding equivalent).
- Mission icons: use `@drawable/ic_mission_math`, `@drawable/ic_mission_shake`, `@drawable/ic_watchdog`, `@drawable/ic_streak`, `@drawable/ic_mission_success`, `@drawable/ic_alarm_enabled`, and `@drawable/ic_alarm_disabled`.
- Card background: `@drawable/bg_springup_surface`; sunrise screen: `@drawable/bg_springup_sunrise`; badges: `@drawable/bg_springup_mission_badge` and `@drawable/bg_springup_watchdog_badge`.
- For a stage update, call `view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pulse_stage))`.
- For mission completion, set the success icon visible and call `startAnimation(AnimationUtils.loadAnimation(context, R.anim.mission_success_pop))`; optionally call the settle animation after it completes, or use a single property animator. Do not loop celebration animations on the ringing screen.

### Dark mode

The package supplies night variants of the new `springup_` color names, and the drawables use those token references. To use these on a screen, point its surfaces/text to `springup_surface_background`, `springup_surface_card`, `springup_text_primary`, `springup_text_secondary` and `springup_divider`. This is additive: it does not silently override the app's current shared colors or force an app-wide theme migration.

### Store artwork

- `store/google_play_feature_graphic_1024x500.png` is a feature graphic at the common 1024×500 canvas size.
- `store/play_screenshot_concept_*.png` are **design concepts/mockups**, not screenshots captured from the compiled app. Replace the phone UI in these concepts with real, current app screenshots before submitting a Play Store listing.
- The three `onboarding_*.png` panels are promotional/onboarding visual designs; use them as references or illustration panels in onboarding UI.

## Design notes

- Main brand red: `#E60012`; light red tint: `#FFF0F1`.
- Sunrise spectrum: `#FFF7E9` → `#FFE3D4` → `#FFC4B5`.
- Watchdog amber: `#F57F17` with pale `#FFF8E1` surfaces.
- Dark mode: ink background `#121216`, card `#1E1E25`, readable off-white text.
- Use illustrations sparingly on the ringing screen so the time, challenge, input controls and progress remain the visual priority.

## Build check

This kit is structured as a resource overlay. Resource XMLs were authored for standard Android drawable/vector formats, but merge it into the repository and run a Gradle build to catch any project-specific resource or theme conflicts. The repository itself was not modified automatically in this session.
