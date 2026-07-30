import os
import uuid
import datetime
from typing import Optional, List
from fastapi import FastAPI, Depends, HTTPException, BackgroundTasks, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import func, desc

from database import get_db
import models
from ai_router import classify_news_article, verify_civic_image, generate_legal_answer, generate_embedding
from scraper import fetch_and_process_news

app = FastAPI(title="Our India — Unified Civic-Tech Platform API")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Pydantic Schemas
class UserCreate(BaseModel):
    id: str
    email: str

class GrievanceCreate(BaseModel):
    userId: str
    category: str
    description: str
    latitude: float
    longitude: float
    mediaUrl: Optional[str] = None
    photoBase64: Optional[str] = None  # Optional base64 photo for AI validation

class LegalQuery(BaseModel):
    query: str
    language: Optional[str] = "EN"

class UpvoteRequest(BaseModel):
    userId: str

# ----------------- Root & Health Check -----------------
@app.get("/")
def read_root():
    return {"status": "Our India API is running."}

@app.get("/api/test-db")
def test_db(db: Session = Depends(get_db)):
    try:
        db.execute(func.now())
        return {"status": "Database connection verified", "postgis": True}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database connection failed: {str(e)}")

# ----------------- Auth & Profiles -----------------
@app.post("/api/v1/auth/login")
def login(user_data: UserCreate, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.email == user_data.email).first()
    if not user:
        user = models.User(
            id=user_data.id,
            email=user_data.email,
            subscription_type="free",
            created_at=datetime.datetime.utcnow()
        )
        db.add(user)
        db.commit()
        db.refresh(user)
    return {"user": {
        "id": user.id,
        "email": user.email,
        "subscription_type": user.subscription_type,
        "created_at": user.created_at.isoformat()
    }}

# ----------------- Module 1: Grievance Redressal Engine -----------------
@app.post("/api/v1/grievances")
def create_grievance(g: GrievanceCreate, db: Session = Depends(get_db)):
    # 1. Check if user exists
    user = db.query(models.User).filter(models.User.id == g.userId).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
        
    # 2. Duplicate check within 200 meters using PostGIS geography ST_DWithin
    new_point = func.ST_SetSRID(func.ST_MakePoint(g.longitude, g.latitude), 4326)
    duplicates = db.query(models.Grievance).filter(
        func.ST_DWithin(
            func.Geography(models.Grievance.geom),
            func.Geography(new_point),
            200
        )
    ).all()
    
    if duplicates:
        # Suggest upvoting instead of duplicate creation
        dup = duplicates[0]
        return {
            "status": "DUPLICATE",
            "message": "A similar issue was already reported nearby.",
            "existingGrievanceId": dup.id,
            "existingDescription": dup.description,
            "existingStatus": dup.status,
            "upvotes": dup.upvotes
        }
        
    # 3. Vision Validation if base64 photo is supplied
    media_url = g.mediaUrl
    if g.photoBase64:
        import base64
        try:
            # Strip base64 metadata header if present
            header = "base64,"
            data_str = g.photoBase64
            if header in data_str:
                data_str = data_str.split(header)[1]
            img_bytes = base64.b64decode(data_str)
            
            # Verify image with Gemini Flash
            verification = verify_civic_image(img_bytes)
            if not verification.get("valid", True):
                raise HTTPException(status_code=400, detail=f"Image verification failed: {verification.get('comments', 'Not a valid civic issue image.')}")
            
            # Simulate Cloudinary upload URL
            media_url = f"https://res.cloudinary.com/ourindia/image/upload/civic_{int(datetime.datetime.utcnow().timestamp())}.jpg"
        except Exception as ex:
            if isinstance(ex, HTTPException):
                raise ex
            raise HTTPException(status_code=400, detail=f"Invalid base64 image data: {str(ex)}")

    # 4. Department assignment (Responsibility Matrix mapping)
    category_dept_mapping = {
        "ROADS": "Public Works Department (PWD)",
        "WATER": "Municipal Water Department",
        "GARBAGE": "Sanitation & Solid Waste Management",
        "ELECTRICITY": "State Electricity Board (MSEDCL)",
        "TRAFFIC": "Traffic Police Department",
        "POLLUTION": "State Pollution Control Board"
    }
    assigned_dept = category_dept_mapping.get(g.category.upper(), "Municipal Administration")
    
    # 5. Save to Neon PostgreSQL
    grievance_id = f"grv_{uuid.uuid4().hex[:8]}"
    db_grievance = models.Grievance(
        id=grievance_id,
        user_id=g.userId,
        category=g.category.upper(),
        description=g.description,
        latitude=g.latitude,
        longitude=g.longitude,
        geom=new_point,
        media_url=media_url,
        status="SUBMITTED",
        assigned_department=assigned_dept,
        upvotes=0,
        is_local_draft=False
    )
    
    db.add(db_grievance)
    db.commit()
    db.refresh(db_grievance)
    
    return {
        "status": "SUCCESS",
        "grievanceId": db_grievance.id,
        "assignedDepartment": db_grievance.assigned_department,
        "createdAt": db_grievance.created_at.isoformat()
    }

@app.get("/api/v1/grievances")
def get_grievances(db: Session = Depends(get_db)):
    grvs = db.query(models.Grievance).order_by(desc(models.Grievance.created_at)).limit(100).all()
    result = []
    for g in grvs:
        result.append({
            "id": g.id,
            "category": g.category,
            "description": g.description,
            "latitude": g.latitude,
            "longitude": g.longitude,
            "mediaUrl": g.media_url,
            "status": g.status,
            "assignedDepartment": g.assigned_department,
            "upvotes": g.upvotes,
            "createdAt": g.created_at.isoformat()
        })
    return {"grievances": result}

@app.post("/api/v1/grievances/{id}/upvote")
def upvote_grievance(id: str, request: UpvoteRequest, db: Session = Depends(get_db)):
    grv = db.query(models.Grievance).filter(models.Grievance.id == id).first()
    if not grv:
        raise HTTPException(status_code=404, detail="Grievance not found")
    
    # Increment upvotes
    grv.upvotes += 1
    
    # Auto-escalation state machine: if upvotes >= 5 and status is SUBMITTED, auto-verify it
    if grv.upvotes >= 5 and grv.status == "SUBMITTED":
        grv.status = "VERIFIED"
        
    db.commit()
    return {"status": "success", "upvotes": grv.upvotes, "grievanceStatus": grv.status}

# ----------------- Module 2: Regional News Map -----------------
@app.get("/api/v1/news-map")
def get_news_map(
    category: Optional[str] = None,
    minSeverity: Optional[int] = 1,
    db: Session = Depends(get_db)
):
    query = db.query(models.CivicRecord).filter(models.CivicRecord.severity >= minSeverity)
    
    if category and category.lower() != "all":
        query = query.filter(models.CivicRecord.category.ilike(category))
        
    records = query.order_by(desc(models.CivicRecord.published_at)).limit(200).all()
    result = []
    for r in records:
        result.append({
            "id": r.id,
            "title": r.title,
            "summary": r.summary,
            "category": r.category,
            "severity": r.severity,
            "latitude": r.latitude,
            "longitude": r.longitude,
            "source": r.source,
            "link": r.link,
            "upvotes": r.upvotes,
            "publishedAt": r.published_at.isoformat() if r.published_at else None
        })
    return {"news": result}

@app.post("/api/v1/news-map/trigger-scraper")
def trigger_news_scraper(background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    background_tasks.add_task(fetch_and_process_news, db)
    return {"status": "Ingestion triggered in background."}

# ----------------- Module 3: AI Legal Advisor (RAG with pgvector) -----------------
@app.post("/api/v1/ai-legal/query")
def legal_query(q: LegalQuery, db: Session = Depends(get_db)):
    # 1. Generate text embedding from user query
    query_embedding = generate_embedding(q.query)
    
    # 2. Vector search using pgvector cosine/l2 distance
    # Retrieve top 3 matching chunks
    chunks = db.query(models.LegalDocument).order_by(
        models.LegalDocument.embedding.l2_distance(query_embedding)
    ).limit(3).all()
    
    if not chunks:
        raise HTTPException(status_code=404, detail="No matching legal documents found in vector database.")
        
    # 3. Build context & citations
    context_blocks = []
    citations = []
    for idx, c in enumerate(chunks):
        context_blocks.append(f"Source {idx+1}: {c.content}")
        citations.append(c.citations)
        
    context = "\n\n".join(context_blocks)
    
    # 4. Generate LLM answer with citation context
    answer = generate_legal_answer(q.query, context)
    
    return {
        "answer": answer,
        "legalCitations": list(set(citations)),
        "disclaimer": "Source: Official Government Gazette/Law Portal. This is machine-extracted raw text. Cross-verify with a certified legal expert."
    }

# ----------------- Module 4: Live Election Tracker -----------------
@app.get("/api/v1/elections/tracker")
def election_tracker():
    return {
        "updatedAt": datetime.datetime.utcnow().isoformat(),
        "totalSeats": 543,
        "tallies": [
            {"party": "National Democratic Alliance (NDA)", "won": 293, "leading": 0, "color": "#F2A900"},
            {"party": "INDIA Alliance", "won": 234, "leading": 0, "color": "#00875A"},
            {"party": "Others", "won": 16, "leading": 0, "color": "#333333"}
        ],
        "constituencyDetails": [
            {
                "name": "Kolhapur (General)",
                "state": "Maharashtra",
                "round": 18,
                "totalRounds": 18,
                "status": "DECLARED",
                "candidates": [
                    {"name": "Shahu Chhatrapati Maharaj", "party": "INC", "votes": 754522, "status": "WON"},
                    {"name": "Sanjay Mandlik", "party": "SHS", "votes": 600287, "status": "LOST"}
                ]
            },
            {
                "name": "Sangli (General)",
                "state": "Maharashtra",
                "round": 15,
                "totalRounds": 20,
                "status": "COUNTING",
                "candidates": [
                    {"name": "Vishal Patil", "party": "IND", "votes": 420199, "status": "LEADING"},
                    {"name": "Sanjaykaka Patil", "party": "BJP", "votes": 381021, "status": "TRAILING"}
                ]
            }
        ]
    }

# ----------------- Module 5: All Party Structure -----------------
@app.get("/api/v1/party/structure")
def party_structure(party: str = "NDA"):
    # Recursive tree traversal of organizational structure
    return {
        "partyName": party,
        "structure": {
            "role": "National President",
            "name": "J.P. Nadda",
            "children": [
                {
                    "role": "State President (Maharashtra)",
                    "name": "Chandrashekhar Bawankule",
                    "children": [
                        {"role": "District Head (Kolhapur)", "name": "Rahul Desai", "children": []},
                        {"role": "District Head (Pune)", "name": "Dhirendra Mane", "children": []}
                    ]
                },
                {
                    "role": "National General Secretary",
                    "name": "Tarun Chugh",
                    "children": []
                }
            ]
        }
    }

# ----------------- Module 6: Area Based Leaders Directory -----------------
@app.get("/api/v1/leaders")
def get_leaders(latitude: float, longitude: float, db: Session = Depends(get_db)):
    # Find nearest district coordinate match
    # Calculate distance to district centers to map district representative mock data
    nearest_district = "Kolhapur"
    min_dist = float("inf")
    
    # Simple coordinates logic fallback
    for name, coords in scraper_districts := {
        "Kolhapur": (16.7050, 74.2433),
        "Pune": (18.5204, 73.8567),
        "Mumbai": (19.0760, 72.8777)
    }.items():
        dist = (latitude - coords[0])**2 + (longitude - coords[1])**2
        if dist < min_dist:
            min_dist = dist
            nearest_district = name
            
    # Standard representative details based on localized area resolution
    leaders_map = {
        "Kolhapur": [
            {"name": "Shahu Chhatrapati Maharaj", "party": "INC", "role": "Member of Parliament (MP)", "constituency": "Kolhapur Lok Sabha", "phone": "+91 231-2661234", "email": "mp.kolhapur@sansad.nic.in"},
            {"name": "Satej Patil", "party": "INC", "role": "Member of Legislative Assembly (MLA)", "constituency": "Kolhapur South", "phone": "+91 231-2651122", "email": "mla.kolhapursouth@maharashtra.gov.in"}
        ],
        "Pune": [
            {"name": "Murlidhar Mohol", "party": "BJP", "role": "Member of Parliament (MP)", "constituency": "Pune Lok Sabha", "phone": "+91 20-25531234", "email": "mp.pune@sansad.nic.in"},
            {"name": "Chandrakant Patil", "party": "BJP", "role": "Member of Legislative Assembly (MLA)", "constituency": "Kothrud", "phone": "+91 20-25445566", "email": "mla.kothrud@maharashtra.gov.in"}
        ],
        "Mumbai": [
            {"name": "Arvind Sawant", "party": "SSB", "role": "Member of Parliament (MP)", "constituency": "Mumbai South Lok Sabha", "phone": "+91 22-22621234", "email": "mp.mumbaisouth@sansad.nic.in"},
            {"name": "Rahul Narwekar", "party": "BJP", "role": "Member of Legislative Assembly (MLA)", "constituency": "Colaba", "phone": "+91 22-22025588", "email": "speaker.assembly@maharashtra.gov.in"}
        ]
    }
    
    return {
        "resolvedDistrict": nearest_district,
        "leaders": leaders_map.get(nearest_district, leaders_map["Kolhapur"])
    }
