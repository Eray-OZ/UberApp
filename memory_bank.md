# UberApp -- Memory Bank

## Project Overview
- **Type:** Uber-style ride-hailing Android app (single app, two modes: Passenger and Driver)
- **Stack:** Kotlin, Jetpack Compose, Firebase (Auth + Firestore + Realtime DB), Google Maps SDK, MVVM, Hilt DI
- **Package:** com.erayoz.uberapp
- **Location:** /Users/erayoz/Codes/UberApp

---

## Phase Log

### Phase 1: Foundation -- COMPLETED
- **Status:** Build verified. All 24 Kotlin source files in place. ./gradlew assembleDebug passed.
- **What was done:**
  - Android Studio project created (Empty Activity, Compose, Kotlin DSL).
  - Firebase project created, google-services.json placed, Auth + Firestore + Realtime DB enabled.
  - Google Cloud Console: Maps SDK for Android + Directions API enabled, API key generated and added to local.properties.
  - Version catalog (libs.versions.toml) with all 20+ dependencies centralized.
  - Project-level and app-level build.gradle.kts configured with all plugins (AGP, Compose, Hilt, KSP, Google Services, Secrets).
  - AndroidManifest.xml: 7 permissions (INTERNET, FINE_LOCATION, COARSE_LOCATION, BACKGROUND_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS), MAPS_API_KEY meta-data, LocationService declaration.
  - MVVM folder structure: data/model (3 models), data/repository (4 repos), di (AppModule), service (LocationService), ui/auth, ui/role, ui/passenger, ui/driver, ui/navigation, ui/theme.
  - Hilt DI wired: UberApp (@HiltAndroidApp), MainActivity (@AndroidEntryPoint), AppModule provides Firebase singletons.
  - Navigation graph with 4 routes: auth, role_selection, passenger_map, driver_map.
  - Material3 theme with custom color tokens and typography.
- **Deviations from plan:**
  - compileSdk 36 instead of 35 (maps-compose 8.3.0 requires it). targetSdk remains 35.
  - KSP version 2.3.6 instead of 2.3.21-1.0.32 (new KSP versioning scheme).
  - hiltViewModel import uses `androidx.hilt.lifecycle.viewmodel.compose` (new path in 1.3.0+).
- **Commit ready:** Yes.

### Phase 2: Authentication and Roles (NOT STARTED)
- Firebase Auth email/password sign-in and sign-up.
- AuthScreen with login/register form UI.
- RoleSelectionScreen with navigation to PassengerMap or DriverMap.
- Firestore user profile creation with selected role.

### Phase 3: Map and Location Services (NOT STARTED)
### Phase 4: Routing / Pathfinder (NOT STARTED)
### Phase 5: The Match (NOT STARTED)

---

## Key Decisions
- Using Navigation Compose 2.x (2.9.8) over Navigation 3 for MVP stability.
- Using KSP instead of deprecated KAPT for annotation processing.
- KTX Firebase modules are deprecated; using main modules with BOM.
- AGP 9.x has built-in Kotlin support; separate kotlin.android plugin not applied at project level.
- Secrets Gradle Plugin (2.0.1) used to inject MAPS_API_KEY from local.properties into the manifest.
- hiltViewModel() uses newer import path from hilt-navigation-compose 1.3.0+.

---

## File Inventory (24 Kotlin source files)
```
app/src/main/java/com/erayoz/uberapp/
  MainActivity.kt, UberApp.kt
  data/model/      -- User.kt, RideRequest.kt, DriverLocation.kt
  data/repository/  -- AuthRepository.kt, UserRepository.kt, LocationRepository.kt, RideRepository.kt
  di/              -- AppModule.kt
  service/         -- LocationService.kt
  ui/auth/         -- AuthScreen.kt, AuthViewModel.kt
  ui/role/         -- RoleSelectionScreen.kt, RoleSelectionViewModel.kt
  ui/passenger/    -- PassengerMapScreen.kt, PassengerViewModel.kt
  ui/driver/       -- DriverMapScreen.kt, DriverViewModel.kt
  ui/navigation/   -- AppNavGraph.kt, Screen.kt
  ui/theme/        -- Color.kt, Theme.kt, Type.kt
```
