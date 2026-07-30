<div align="center">

# 🇮🇳 Our India
### Unified Civic-Tech Platform — Native Android

**AI-powered public grievance, news intelligence & civic transparency app for Indian citizens**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/compose)
[![Hilt](https://img.shields.io/badge/DI-Hilt%20(Dagger)-FF6F00?style=for-the-badge)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Offline-Room%20DB-009688?style=for-the-badge)](https://developer.android.com/training/data-storage/room)
[![Maps](https://img.shields.io/badge/Maps-Google%20Maps%20SDK-34A853?style=for-the-badge&logo=googlemaps)](https://developers.google.com/maps)
[![Cost](https://img.shields.io/badge/Monthly%20Cost-%240-brightgreen?style=for-the-badge)](/)

</div>

---

## 🎯 What Problem Does It Solve?

Across India, civic information is **fragmented** across 100+ portals, news sites, and government systems. Citizens can't easily:

- Report local issues (potholes, water shortage) and track their status
- See which civic problems are most critical in their district
- Get plain-language legal advice about their rights
- Know which MP/MLA/Corporator represents their area
- Follow election results with context

**Our India** solves all of this in a **single, offline-first native Android application**.

---

## 🏗️ System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    NATIVE ANDROID APP (Kotlin)                       │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                   PRESENTATION LAYER                           │  │
│  │  Jetpack Compose  │  ViewModels  │  StateFlow  │  Material 3  │  │
│  │  Google Maps SDK  │  CameraX     │  Neo-Brutalism Theme       │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │                      DOMAIN LAYER                              │  │
│  │  Use Cases  │  Domain Models  │  Repository Interfaces         │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │                       DATA LAYER                               │  │
│  │  Room SQLite (Offline)  │  Retrofit (Remote)  │  WorkManager   │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
└──────────────────────────────┼───────────────────────────────────────┘
                               │ HTTPS / REST
┌──────────────────────────────▼───────────────────────────────────────┐
│                    FASTAPI BACKEND (Server)                          │
│  Grievances │ News Scraper │ AI Router (Groq → Gemini) │ Legal RAG  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│              NEON POSTGRESQL (PostGIS + pgvector)                    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 🧩 The Six Core Modules

### 1. 📋 Grievance Redressal Engine
Citizens report civic issues with **native camera** + **GPS pin-drop**. AI validates the photo (Gemini Vision), checks for duplicates within 200m (PostGIS), and routes to the correct government department automatically. Offline submissions are queued in Room and synced via WorkManager.

### 2. 🗺️ Regional News Map
Live civic issues scraped from Google News RSS → classified by AI (Groq Llama 3.3) → displayed as color-coded markers on a **native Google Map**. Red = critical, Yellow = moderate, Green = low. Bottom sheet shows AI summary + upvote button.

### 3. ⚖️ AI Legal Advisor (RAG)
Citizens ask legal questions in plain language → query is embedded (Gemini Embedding-2, 3072D) → matched against indexed Indian laws via pgvector → answer generated with citations. Cached locally for 30 days.

### 4. 🗳️ Live Election Tracker
Real-time constituency-level results with party-wise tallies, candidate vote bars, and round-by-round counting. Mock data available year-round for demonstration.

### 5. 🏛️ All Party Structure
Visual expandable tree of political party hierarchies from National → State → District → Ward. Updated monthly. Fully cached offline in Room.

### 6. 👤 Area-Based Leaders Directory
Enter GPS location → instantly see your MP, MLA, Corporator, Mayor, Commissioner with contact details. One-tap call/email actions.

---

## 🧠 AI Routing Strategy

| Task | Primary Model | Fallback |
|------|--------------|----------|
| News Classification | Groq (Llama-3.3-70B) | Gemini 2.5 Flash |
| Photo Verification | Gemini 2.5 Flash | Auto-approve |
| Legal RAG Answer | Groq (Llama-3.3-70B) | Gemini 2.5 Flash |
| Text Embeddings | Gemini Embedding-2 (3072D) | Zero vector |

---

## 📱 Android Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | Clean Architecture + MVVM |
| **DI** | Hilt (Dagger) |
| **Local DB** | Room (SQLite) — 7 tables |
| **Networking** | Retrofit 2 + KotlinX Serialization |
| **Background Sync** | WorkManager (6-hour periodic) |
| **Maps** | Google Maps SDK for Android |
| **Camera** | CameraX |
| **Image Loading** | Coil 3 |
| **Logging** | Timber |
| **Build** | Gradle Kotlin DSL + Version Catalog |

---

## 📁 Project Structure

```
Our-India/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/ourindia/app/
│   │   │   │   ├── OurIndiaApplication.kt       # Hilt entry point
│   │   │   │   ├── MainActivity.kt               # Compose host activity
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── entity/Entities.kt    # 7 Room entities
│   │   │   │   │   │   ├── dao/Daos.kt           # All DAOs with Flow
│   │   │   │   │   │   └── OurIndiaDatabase.kt   # Room DB class
│   │   │   │   │   └── remote/
│   │   │   │   │       └── OurIndiaApiService.kt  # Retrofit endpoints
│   │   │   │   ├── di/                            # Hilt modules
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/
│   │   │   │   │   │   ├── Color.kt              # Civic Brutalism palette
│   │   │   │   │   │   ├── Type.kt               # Typography scale
│   │   │   │   │   │   └── Theme.kt              # Dual-mode theme
│   │   │   │   │   ├── components/                # NeoCard, NeoButton
│   │   │   │   │   ├── screens/                   # All 6 module screens
│   │   │   │   │   └── navigation/                # NavHost + bottom bar
│   │   │   │   └── worker/                        # WorkManager SyncWorker
│   │   │   └── res/
│   │   │       └── values/
│   │   │           ├── strings.xml
│   │   │           └── themes.xml
│   │   ├── build.gradle.kts                       # App dependencies
│   │   └── proguard-rules.pro
│   ├── gradle/libs.versions.toml                  # Version catalog
│   ├── build.gradle.kts                           # Project-level
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── local.properties                           # 🔒 API keys (NOT committed)
│
├── .gitignore
└── README.md                                      # ← This file
```

---

## 🗄️ Room Database (Offline Cache)

| Table | Records | TTL | Purpose |
|-------|---------|-----|---------|
| `cached_issues` | 500 max | 7 days | News map civic issues |
| `grievances` | User's + nearby | — | Complaints + drafts |
| `legal_cache` | Q&A pairs | 30 days | Legal RAG response cache |
| `party_structure` | Full tree | Monthly | Party org hierarchy |
| `leaders` | By district | 7 days | Elected representatives |
| `offline_queue` | Pending actions | 24 hours | Sync queue |
| `election_results` | All results | Per sync | Election data cache |

---

## ⚡ Getting Started

### Prerequisites
- Android Studio Hedgehog+ (2024.1+)
- JDK 17
- Android SDK 35
- A Google Maps API Key

### 1. Clone

```bash
git clone https://github.com/harshoswal1/Our-India.git
cd Our-India/android
```

### 2. Configure API Keys

Create `android/local.properties`:
```properties
MAPS_API_KEY=your_google_maps_api_key_here
API_BASE_URL=http://10.0.2.2:8000
```

### 3. Build & Run

```bash
# Open in Android Studio, or:
./gradlew assembleDebug
```

Install the APK on an emulator or physical device.

---

## 🔒 Security

| Protection | Implementation |
|-----------|----------------|
| API keys | Loaded from `local.properties` → `BuildConfig` (never committed) |
| ProGuard/R8 | Release builds minified & obfuscated |
| Certificate Pinning | SHA-256 pins in OkHttp (production) |
| Room Encryption | SQLCipher ready |
| Input Sanitization | All user input sanitized before API calls |

---

## 🌐 Backend API Endpoints (Consumed by Android)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/auth/login` | Firebase Auth login |
| `POST` | `/api/v1/grievances` | Submit grievance |
| `GET` | `/api/v1/grievances` | Get grievances |
| `POST` | `/api/v1/grievances/{id}/upvote` | Upvote |
| `GET` | `/api/v1/news-map` | Get classified news |
| `POST` | `/api/v1/ai-legal/query` | Legal question (RAG) |
| `GET` | `/api/v1/elections/tracker` | Election data |
| `GET` | `/api/v1/party/structure` | Party tree |
| `GET` | `/api/v1/leaders?lat=&lng=` | Leaders by GPS |

---

## 💰 Total Monthly Cost: $0

| Service | Free Tier |
|---------|-----------|
| **Neon PostgreSQL** | 500MB + PostGIS + pgvector |
| **Groq** | 30 RPM, 10K req/day |
| **Google Gemini** | 1,500 req/day |
| **Google Maps SDK** | $200/month free credit |

---

## 🤝 Contributing

1. Fork → `git checkout -b feature/my-feature`
2. Commit → `git push origin feature/my-feature`
3. Open a Pull Request

---

## 📜 License

MIT License — Free to use, modify, and distribute.

---

<div align="center">

Built for the citizens of India 🇮🇳 — Civic transparency powered by AI

</div>
