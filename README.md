<div align="center">

# 🇮🇳 Our India — Political Party Structure, Hierarchy & Civic Intelligence

**A high-performance, offline-first Native Android platform and autonomous ingestion engine for India's complete political hierarchy, boundary mapping, and civic leadership intelligence.**

[![Android](https://img.shields.io/badge/Platform-Android%20Native-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Supabase](https://img.shields.io/badge/Cloud%20DB-Supabase%20PostgreSQL-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)](https://supabase.com)
[![MapLibre](https://img.shields.io/badge/Maps-MapLibre%20Native-000000?style=for-the-badge&logo=maplibre&logoColor=white)](https://maplibre.org)
[![Room](https://img.shields.io/badge/Local%20Cache-Room%20DB-009688?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![GitHub Actions](https://img.shields.io/badge/Ingestion-GitHub%20Actions%20CI%2FCD-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/features/actions)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

</div>

---

## ⚡ System Architecture

```mermaid
flowchart TD
    subgraph S["🌐 Public Authoritative Sources"]
        S1["⚖️ Election Commission of India (ECI)"]
        S2["🚩 BJP National Portals / Gazette"]
        S3["✋ INC AICC Leadership Data"]
        S4["🧹 AAP National Leadership Portals"]
    end

    subgraph W["🤖 Autonomous Ingestion Worker (GitHub Actions)"]
        A1["📡 Modular Source Adapters"]
        A2["🔍 Parser & Pydantic Validation"]
        A3["🏛️ Entity Resolution & Core Seeding"]
        A4["📜 Historical Assignment Engine"]
        A5["📊 Hard Post-Ingestion Verification"]
    end

    subgraph C["☁️ Supabase Cloud Database"]
        DB1["parties"]
        DB2["political_organization_units"]
        DB3["political_positions (43+ Verified)"]
        DB4["politicians"]
        DB5["political_position_assignments (Active/Historical)"]
        DB6["source_registry & verification_records"]
    end

    subgraph A["📱 Android Application (Offline-First)"]
        R1["🔄 PoliticalSyncRepository (Delta Sync)"]
        R2["🗄️ Room Local SQLite Cache"]
        R3["🧠 Shared State ViewModels"]
        R4["📍 LocalGeographicResolver (Ray-Casting)"]
        
        UI1["🏛️ Infinite Hierarchy Canvas"]
        UI2["🗺️ MapLibre Explorer"]
        UI3["📊 Civic Analytics Dashboard"]
        UI4["👤 Politician Profile & Timeline"]
    end

    S --> A1
    A1 --> A2 --> A3 --> A4 --> A5 --> C
    C -- "Incremental Delta Sync (versionDate > lastSync)" --> R1
    R1 --> R2 --> R3
    R4 --> R3
    R3 --> UI1 & UI2 & UI3 & UI4
```

---

## ✨ Core Pillars & Features

<div align="center">

| 🏛️ **Position-Driven Hierarchy** | 🗺️ **Map Explorer & PIP Engine** | 📊 **Dynamic Civic Analytics** |
|:---:|:---:|:---:|
| **Infinite Pan/Zoom Canvas** with 50% initial scale, root-centering, and multiple positions per tier. Unknown holders show *"Not yet fetched"*—never fabricated. | **MapLibre Native Vector Map** with offline Ray-Casting Point-in-Polygon resolving State ➔ District ➔ Sub-district on long-press. | **Shared Multi-Module Filter** calculating seats, regional distribution, and demographics. Restores nationwide view instantly on clear. |

| 🔄 **Delta Sync & Offline Room Cache** | 🤖 **Autonomous Ingestion Worker** | 📜 **True Historical Preservation** |
|:---:|:---:|:---:|
| Sub-second startup from local Room DB. Incremental delta sync over PostgREST without blocking the UI thread. | Bi-monthly GitHub Actions cron running real scrapers with deterministic UUID seeding and zero secret leaks. | When office holders change: old assignment is marked `is_active=false, effective_to=now()`; new assignment is activated. History is never wiped. |

</div>

---

## 🚀 Key Engineering & Performance Optimizations

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 1. Zero-Dependency Geographic Engine                                                   │
│    • Compressed 3 administrative boundaries into 0.81 MB GZIP GeoJSONs                 │
│    • Custom Kotlin Ray-Casting algorithm resolves Point-in-Polygon in < 2ms            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 2. Position-First Data Integrity                                                       │
│    • Strict separation between Positions (Designations) and Politicians (People)      │
│    • Positions exist permanently even if the current office holder is unknown          │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 3. Deterministic Supabase Cloud Synchronization                                        │
│    • Deterministic UUIDv5 primary keys guarantee idempotent, conflict-free writes      │
│    • Dynamic schema adaptation automatically adjusts payloads to remote table columns  │
│    • Hard fail-safe aborts CI/CD run if core production tables remain empty            │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema & Data Models

```
☁️ Supabase PostgreSQL (Production)
├── parties                          (id, name, short_name, symbol, status)
├── political_organization_units     (id, party_id, official_name, unit_type, hierarchy_level)
├── political_positions              (id, party_id, official_title, hierarchy_level, position_type)
├── politicians                      (id, name, party_id, photo, biography, status)
├── political_position_assignments   (id, position_id, politician_id, effective_from, effective_to, is_active)
├── source_registry                  (source_id, source_name, url, source_type, authority_level)
└── verification_records             (id, source_id, record_id, verification_status, confidence)
```

---

## 🧪 Production Verification & Build Metrics

| Metric | Status / Value | Verification Detail |
|---|:---:|---|
| **Android Unit Tests** | `PASS` | `testDebugUnitTest` 100% passing (0 failures) |
| **Android Debug Build** | `PASS` | `assembleDebug` clean build completed in 1m 50s |
| **Debug APK Location** | `READY` | `android/app/build/outputs/apk/debug/app-debug.apk` |
| **APK Binary Size** | `65.8 MB` | Well within target budget (< 100 MB) |
| **Live Scraper Adapters** | `PASS` | 44 real verified records extracted across ECI, BJP, INC, AAP |
| **Supabase Cloud Population**| `VERIFIED` | 43+ positions, parties, politicians & active assignments populated |
| **Security Audit** | `CLEAN` | Zero service-role keys or private credentials committed |

---

## 🛠️ Tech Stack

- **Android Client**: Kotlin 2.0, Jetpack Compose, Material 3, AndroidX Navigation, Hilt DI, Coroutines & Flow.
- **Local Persistence**: Room SQLite, GZIP Compressed GeoJSON Assets.
- **Maps & GIS**: MapLibre Native SDK, Custom Ray-Casting Point-in-Polygon Engine.
- **Cloud Backend**: Supabase PostgreSQL, Row Level Security (RLS), PostgREST.
- **Data Ingestion**: Python 3.12, BeautifulSoup4, Requests, Pydantic, GitHub Actions Automation.

---

## 🏁 Quick Start & Building

### 1. Android Application

```bash
# Clone the repository
git clone https://github.com/harshoswal1/Our-India.git
cd Our-India/android

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### 2. Autonomous Ingestion Worker (Python)

```bash
# From repository root
pip install requests beautifulsoup4 pydantic supabase

# Execute worker module
python -m backend.main
```

*(Requires `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` environment variables configured).*

---

## 🔒 Security Architecture

- **Public Client Isolation**: Android communicates with Supabase exclusively via the public publishable/anonymous API key with Row Level Security (RLS) policies.
- **Protected Service Keys**: Privileged database writes are restricted to GitHub Actions runner environments via encrypted repository secrets.
- **Audit**: Zero private tokens, JWTs, or service-role keys exist in Android code, assets, or Git history.

---

<div align="center">

**Our India — Engineered for Civic Transparency & Democratic Empowerment 🇮🇳**

</div>
