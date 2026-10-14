# 🏛️ Political Party Structure Feature Module

## Overview

The **Political Party Structure** feature module in the **Our India** Android application provides citizens with a transparent, visual hierarchy of political party organizations across India — from **National Leadership** down to **State, District, and Ward-level representatives**.

---

## 📁 Android Module File Architecture

This module is isolated in its own dedicated package folder inside the Android app:

```text
android/app/src/main/java/com/ourindia/app/ui/partystructure/
├── PartyStructureModels.kt       # HierarchyLevel, PartyTreeNode, PartyCatalog, UiState
├── PartyStructureComponents.kt   # PartyHeaderCard, PartySearchBar, LevelFilterChips, PartyTreeNodeItem, LeaderDetailBottomSheet
├── PartyStructureViewModel.kt    # StateFlow, Room DAO observation, Search & Level Filtering, Tree construction & Seed data
└── PartyStructureScreen.kt       # Main Jetpack Compose Screen entry point
```

---

## 🚀 How to Run & Test on Your Mobile Phone

### Prerequisites
1. **Android Smartphone** (Android 8.0 / API 26 or higher).
2. **USB Cable** or **Wi-Fi network**.
3. **Android Studio** (Hedgehog+) or **JDK 17** with Android SDK installed on your computer.

---

### Option 1: Direct Install via USB Debugging (Recommended)

1. **Enable Developer Options on your phone**:
   - Go to **Settings** → **About Phone**.
   - Tap **Build Number** 7 times until you see `"You are now a developer!"`.

2. **Enable USB Debugging**:
   - Go to **Settings** → **System** → **Developer Options**.
   - Turn ON **USB Debugging**.

3. **Connect Phone to PC**:
   - Plug in your phone using a USB cable.
   - When prompted on your phone screen, select **"Allow USB Debugging"**.

4. **Build & Install the App**:
   Open a terminal inside the project directory and run:
   ```powershell
   cd android
   .\gradlew.bat installDebug
   ```
   *Or open the project in Android Studio, select your phone from the device dropdown at the top, and click the green **Run (▶)** button.*

---

### Option 2: Build APK & Transfer to Phone

If you want to send the app to your phone without USB cable:

1. **Build the Debug APK**:
   ```powershell
   cd android
   .\gradlew.bat assembleDebug
   ```

2. **Locate the Generated APK File**:
   The built APK will be at:
   `android/app/build/outputs/apk/debug/app-debug.apk`

3. **Install on Phone**:
   - Transfer `app-debug.apk` to your phone via Google Drive, WhatsApp, Telegram, or USB file transfer.
   - Open your phone's File Manager, tap `app-debug.apk`, and tap **Install** (Allow "Install from unknown sources" if prompted).

---

### Option 3: Wireless Debugging (Android 11+)

1. Connect your phone and PC to the same Wi-Fi network.
2. Go to **Developer Options** → **Wireless Debugging** → Turn ON.
3. Tap **Pair device with pairing code**.
4. In terminal run:
   ```powershell
   adb pair <IP_ADDRESS>:<PORT>
   adb connect <IP_ADDRESS>:<PORT>
   .\gradlew.bat installDebug
   ```

---

## 🎨 Key Features & UI Specs

- **Party Selector Chips**: Instant switching between **BJP, INC, AAP, NCP, TMC, DMK**.
- **Interactive Tree View**: Expandable/collapsible hierarchy branches from National → State → District → Ward.
- **Real-time Filter & Search**: Search leaders by name, role title, state, or district.
- **Level Filter**: Filter nodes by level (*National*, *State*, *District*, *Ward*).
- **Offline Cache**: Fully cached in Room DB for instant offline viewing.
- **Leader Detail Modal**: Tap any leader to view full jurisdiction, contact actions, and sharing options.
