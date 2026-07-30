<div align="center">

# 🇮🇳 Our India
### Unified Civic-Tech Platform

**AI-powered public grievance, news intelligence & civic transparency platform for Indian citizens**

[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?style=for-the-badge&logo=fastapi)](https://fastapi.tiangolo.com)
[![Next.js](https://img.shields.io/badge/Frontend-Next.js%2016-black?style=for-the-badge&logo=next.js)](https://nextjs.org)
[![PostgreSQL](https://img.shields.io/badge/Database-Neon%20PostgreSQL-336791?style=for-the-badge&logo=postgresql)](https://neon.tech)
[![Groq](https://img.shields.io/badge/AI-Groq%20Llama%203.3-FF6B35?style=for-the-badge)](https://groq.com)
[![Gemini](https://img.shields.io/badge/AI-Google%20Gemini-4285F4?style=for-the-badge&logo=google)](https://ai.google.dev)
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

**Our India** solves all of this in a **single, AI-powered platform**.

---

## 🏗️ How It Works — System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        USER (Browser / Android)                     │
└─────────────────────────┬───────────────────────────────────────────┘
                          │  HTTP Requests
┌─────────────────────────▼───────────────────────────────────────────┐
│                 NEXT.JS FRONTEND  (Port 3000)                       │
│   Dashboard  │  Grievances  │  Legal Chat  │  Elections  │  Leaders │
└─────────────────────────┬───────────────────────────────────────────┘
                          │  REST API Calls
┌─────────────────────────▼───────────────────────────────────────────┐
│                  FASTAPI BACKEND  (Port 8000)                       │
│                                                                     │
│  ┌────────────┐  ┌──────────────┐  ┌───────────────────────────┐   │
│  │  Grievance │  │  News Scraper│  │   AI Router               │   │
│  │  Engine    │  │  (RSS Feeds) │  │  Groq ──► Gemini Fallback │   │
│  └────────────┘  └──────────────┘  └───────────────────────────┘   │
│                                                                     │
│  ┌────────────┐  ┌──────────────┐  ┌───────────────────────────┐   │
│  │   Legal    │  │  Elections   │  │  Leaders Directory        │   │
│  │  RAG Chat  │  │  Tracker     │  │  (PostGIS Resolution)     │   │
│  └────────────┘  └──────────────┘  └───────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────────────┘
                          │  SQL / Vector Queries
┌─────────────────────────▼───────────────────────────────────────────┐
│              NEON POSTGRESQL DATABASE (Cloud)                       │
│   PostGIS (Spatial)  │  pgvector (AI Embeddings)  │  uuid-ossp     │
│                                                                     │
│   Tables: grievances │ civic_records │ user_profiles │ area_leaders │
│            legal_documents │ lgd_jurisdictions │ audit_logs        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🧩 The Six Core Modules

### Module 1 — 📋 Grievance Redressal Engine

> Citizens report civic issues, the AI validates them and routes them to the right department.

```
User Reports Issue
       │
       ▼
 GPS Location Captured
       │
       ▼
 Duplicate Check (PostGIS 200m radius)
       │
       ├── Duplicate Found → Suggest Upvote Instead
       │
       └── New Issue
               │
               ▼
         Gemini Vision validates photo
               │
               ▼
         AI assigns Department (Responsibility Matrix)
               │
               ▼
         Saved to PostgreSQL
               │
               ▼
         Status: SUBMITTED → VERIFIED (5 upvotes) → IN_PROGRESS → RESOLVED
```

**Department Routing Matrix:**

| Category | Assigned Department |
|----------|---------------------|
| ROADS | Public Works Department (PWD) |
| WATER | Municipal Water Department |
| GARBAGE | Sanitation & Solid Waste Management |
| ELECTRICITY | State Electricity Board (MSEDCL) |
| TRAFFIC | Traffic Police Department |
| POLLUTION | State Pollution Control Board |

---

### Module 2 — 🗺️ Regional News Map & AI Pipeline

> Live civic issues scraped from Google News RSS, classified by AI, and pinned on an interactive district map.

```
Google News RSS Feeds
       │  (36 Maharashtra Districts × Keywords)
       ▼
 feedparser + BeautifulSoup4
  → Clean article text
       │
       ▼
 MD5 Deduplication
  → Skip if already in DB
       │
       ▼
 Groq Llama-3.3-70B (Fast LPU)
  → JSON Classification Output:
     • core_topic (Water Scarcity / Infrastructure / Crime...)
     • problem_rate (0–100 severity)
     • government_body (responsible dept)
     • specific_location
     • ai_suggested_solution
       │
       ▼
 Severity Mapped to 1–5 Scale
       │
       ▼
 Stored in civic_records with PostGIS Point geometry
       │
       ▼
 Interactive Map: Red (Sev 4–5) │ Yellow (2–3) │ Green (1)
```

**Covered Districts:** Kolhapur, Pune, Nashik, Nagpur, Aurangabad, Thane, Mumbai (+ 29 more)

---

### Module 3 — ⚖️ AI Legal Advisor (RAG Pipeline)

> Citizen asks a legal question → AI searches official legal documents → Returns answer with citations.

```
User Question: "Can police tow my vehicle if I'm sitting in it?"
       │
       ▼
 Gemini Embedding-2 (3072-dimensional vector)
  → Embed the query
       │
       ▼
 pgvector Cosine Search on legal_documents table
  → Top 3 matching legal chunks retrieved
       │
       ▼
 Context: Motor Vehicles Act + DVR 2017 + Court precedents
       │
       ▼
 Groq Llama-3.3-70B
  → Generates answer with citations
       │
       ▼
 Response includes:
  • Plain language answer
  • Legal citations (Act name + section)
  • Disclaimer: "Consult a certified legal expert"
```

**Legal Sources Indexed:**
- Motor Vehicles Act, 1988
- Right to Information Act, 2005
- Municipal Corporation Acts
- Bharatiya Nyaya Sanhita (BNS)
- High Court Precedents

---

### Module 4 — 🗳️ Live Election Tracker

> National seat tallies, state-wise breakdown, constituency-level counting rounds.

**Features:**
- Party-wise seat totals (NDA / INDIA Alliance / Others)
- Constituency drill-down with candidate vote counts
- Round-by-round counting progress
- Lead/trailing status per candidate

---

### Module 5 — 🏛️ All Party Structure

> Visual tree of political party organization from national level down to district heads.

```
National President (J.P. Nadda)
       └── State President (Maharashtra) - Chandrashekhar Bawankule
               ├── District Head (Kolhapur) - Rahul Desai
               └── District Head (Pune) - Dhirendra Mane
```

---

### Module 6 — 👤 Area-Based Leaders Directory

> Enter your GPS location → instantly see your MP, MLA, Corporator, Mayor, and Commissioner.

```
User Location (lat, lng)
       │
       ▼
 PostGIS Point-in-Polygon Matching
  → Resolves to LGD Ward Code
       │
       ▼
 Returns:
  • MP → Name, Party, Constituency, Phone, Email
  • MLA → Name, Party, Constituency, Phone, Email
  • Corporator / Mayor → Local contacts
```

---

## 🧠 AI Routing Strategy

The platform distributes AI tasks intelligently to avoid throttling and stay within **zero-cost free tiers**.

| Task | Primary Model | Fallback | Free Tier Limit |
|------|--------------|----------|-----------------|
| News Classification | **Groq** (Llama-3.3-70B) | Gemini 2.5 Flash | 30 RPM |
| Image Verification | **Gemini** 2.5 Flash | Auto-approve | 1,500 req/day |
| Legal RAG Answer | **Groq** (Llama-3.3-70B) | Gemini 2.5 Flash | 10K req/day |
| Text Embeddings | **Gemini** Embedding-2 (3072D) | Zero vector | 1,500 req/day |

**Fallback Logic:**
```python
try:
    answer = groq_client.chat()   # Primary
except:
    answer = gemini_client.generate()  # Fallback
```

---

## 🗄️ Database Schema

```
┌─────────────────────────────────────────────────────────────────────┐
│                    NEON POSTGRESQL                                   │
│                                                                     │
│  user_profiles          grievances                                  │
│  ┌──────────────┐       ┌────────────────────────────────────┐     │
│  │ id (str)     │──┐    │ id, user_id (FK), category        │     │
│  │ email        │  └───►│ description, latitude, longitude   │     │
│  │ subscription │       │ geom GEOMETRY(POINT, 4326)         │     │
│  │ fcm_token    │       │ status, assigned_department        │     │
│  │ created_at   │       │ media_url, upvotes                 │     │
│  └──────────────┘       └────────────────────────────────────┘     │
│                                                                     │
│  civic_records           legal_documents                           │
│  ┌──────────────┐        ┌──────────────────────────────────┐     │
│  │ id, title    │        │ id, title, content               │     │
│  │ summary      │        │ citations                        │     │
│  │ category     │        │ embedding VECTOR(3072)  ◄── AI   │     │
│  │ severity 1-5 │        └──────────────────────────────────┘     │
│  │ geom POINT   │                                                   │
│  │ dedup_hash   │        area_leaders                              │
│  │ source, link │        ┌──────────────────────────────────┐     │
│  └──────────────┘        │ id, name, party, role            │     │
│                           │ constituency, lgd_code           │     │
│  lgd_jurisdictions        │ contact_phone, contact_email     │     │
│  ┌──────────────┐        └──────────────────────────────────┘     │
│  │ lgd_code     │                                                   │
│  │ name, level  │  ← STATE / DISTRICT / WARD                      │
│  │ boundary     │                                                   │
│  │ POLYGON 4326 │                                                   │
│  └──────────────┘                                                   │
└─────────────────────────────────────────────────────────────────────┘

Extensions: PostGIS │ postgis_topology │ uuid-ossp │ pgcrypto │ pgvector
```

---

## 📁 Project Structure

```
Our-India/
│
├── backend/                    ← FastAPI Python Server
│   ├── main.py                 # All 6 module REST endpoints
│   ├── ai_router.py            # Groq + Gemini AI orchestration
│   ├── scraper.py              # Google News RSS ingestion pipeline
│   ├── models.py               # SQLAlchemy + GeoAlchemy2 + pgvector models
│   ├── database.py             # Neon PostgreSQL connection pool
│   ├── seed_db.py              # Legal document seeder (with real embeddings)
│   ├── init_db.py              # Enable PostGIS, pgvector, uuid-ossp extensions
│   ├── create_tables.py        # Create all DB tables from models
│   ├── requirements.txt        # Python dependencies
│   └── .env                    # API Keys & Database URL (not committed)
│
├── frontend/                   ← Next.js 16 Web App
│   ├── src/app/
│   │   ├── page.tsx            # Landing page & region search
│   │   └── dashboard/
│   │       └── page.tsx        # Issues dashboard with Recharts
│   ├── package.json
│   └── next.config.ts
│
└── README.md                   ← This file
```

---

## ⚡ Getting Started

### Prerequisites
- Python 3.10+
- Node.js 18+
- Git

### 1. Clone the repository

```bash
git clone https://github.com/your-org/Our-India.git
cd Our-India
```

### 2. Backend Setup

```bash
cd backend

# Create virtual environment
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # Mac/Linux

# Install dependencies
pip install -r requirements.txt

# Configure environment variables
cp .env.example .env
# Edit .env with your credentials (see Environment Variables section)

# Initialize database (enable PostGIS, pgvector)
python init_db.py

# Create all tables
python create_tables.py

# Seed legal documents with AI embeddings
python seed_db.py

# Start the server
uvicorn main:app --reload --port 8000
```

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start the dev server
npm run dev
```

### 4. Access the App

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8000 |
| API Docs (Swagger) | http://localhost:8000/docs |

---

## 🔑 Environment Variables

Create a `backend/.env` file:

```env
# Neon PostgreSQL (get free at neon.tech)
DATABASE_URL=postgresql://user:pass@ep-xxxx.neon.tech/neondb?sslmode=require

# Groq API (free at console.groq.com)
GROQ_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxx

# Google Gemini API (free at aistudio.google.com)
GEMINI_API_KEY=AIzaxxxxxxxxxxxxxxxxxxxx
```

---

## 🌐 API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | Health check |
| `GET` | `/api/test-db` | Verify database connection |
| `POST` | `/api/v1/auth/login` | Create/fetch user profile |
| `POST` | `/api/v1/grievances` | Submit new grievance |
| `GET` | `/api/v1/grievances` | Get all grievances |
| `POST` | `/api/v1/grievances/{id}/upvote` | Upvote a grievance |
| `GET` | `/api/v1/news-map` | Get classified civic news |
| `POST` | `/api/v1/news-map/trigger-scraper` | Trigger background scraper |
| `POST` | `/api/v1/ai-legal/query` | Ask a legal question (RAG) |
| `GET` | `/api/v1/elections/tracker` | Get election tally data |
| `GET` | `/api/v1/party/structure` | Get party org tree |
| `GET` | `/api/v1/leaders?latitude=&longitude=` | Leaders by location |

---

## 💰 Total Monthly Cost: $0

| Service | What We Use | Free Tier |
|---------|------------|-----------|
| **Neon** | PostgreSQL + PostGIS + pgvector | 500MB, 50GB bandwidth |
| **Groq** | Llama-3.3-70B for NLP & legal | 30 RPM, 10K req/day |
| **Google Gemini** | Vision + Embeddings | 1,500 req/day |
| **Vercel/Railway** | Backend + Frontend hosting | Free tier |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | Next.js 16, React 19, Tailwind CSS, Recharts |
| **Backend** | FastAPI, Uvicorn, Python 3.12 |
| **Database** | PostgreSQL (Neon), PostGIS, pgvector |
| **ORM** | SQLAlchemy 2.0, GeoAlchemy2 |
| **AI - NLP** | Groq API (Llama-3.3-70B-Versatile) |
| **AI - Vision** | Google Gemini 2.5 Flash |
| **AI - Embeddings** | Gemini Embedding-2 (3072D) |
| **Web Scraping** | feedparser, BeautifulSoup4 |
| **Spatial** | PostGIS, GeoAlchemy2 |
| **Vector Search** | pgvector (cosine / L2 distance) |

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push to the branch: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 📜 License

MIT License — Free to use, modify, and distribute.

---

<div align="center">

Built for the citizens of India 🇮🇳 — Civic transparency powered by AI

</div>
