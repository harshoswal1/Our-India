import os
import hashlib
import urllib.parse
import feedparser
from bs4 import BeautifulSoup
from datetime import datetime, timedelta, timezone
from sqlalchemy import func
from database import SessionLocal
from models import CivicRecord
from ai_router import classify_news_article

# District coordinate catalog for spatial geocoding
DISTRICTS = {
    "Ahmednagar": (19.0948, 74.7480),
    "Akola": (20.7002, 77.0082),
    "Amravati": (20.9320, 77.7523),
    "Aurangabad": (19.8762, 75.3433),
    "Beed": (18.9891, 75.7601),
    "Bhandara": (21.1718, 79.9924),
    "Buldhana": (20.5292, 76.1843),
    "Chandrapur": (19.9615, 79.2961),
    "Dhule": (20.9042, 74.7749),
    "Gadchiroli": (20.1005, 79.9934),
    "Gondia": (21.4598, 80.1951),
    "Hingoli": (19.7214, 77.1519),
    "Jalgaon": (21.0077, 75.5626),
    "Jalna": (19.8410, 75.8834),
    "Kolhapur": (16.7050, 74.2433),
    "Latur": (18.4088, 76.5604),
    "Mumbai City": (18.9690, 72.8210),
    "Mumbai Suburban": (19.1278, 72.8465),
    "Nagpur": (21.1458, 79.0882),
    "Nanded": (19.1383, 77.3210),
    "Nandurbar": (21.7469, 74.1240),
    "Nashik": (19.9975, 73.7898),
    "Osmanabad": (18.1861, 76.0419),
    "Palghar": (19.6974, 72.7661),
    "Parbhani": (19.2608, 76.7748),
    "Pune": (18.5204, 73.8567),
    "Raigad": (18.5158, 73.1822),
    "Ratnagiri": (16.9944, 73.3000),
    "Sangli": (16.8524, 74.5815),
    "Satara": (17.6805, 73.9918),
    "Sindhudurg": (16.1214, 73.5610),
    "Solapur": (17.6599, 75.9064),
    "Thane": (19.2183, 72.9781),
    "Wardha": (20.7453, 78.6022),
    "Washim": (20.1009, 77.1353),
    "Yavatmal": (20.3888, 78.1228)
}

KEYWORDS = ["problem", "complaint", "crisis", "accident", "shortage", "potholes", "water supply"]

def generate_google_news_rss_url(query: str):
    encoded_query = urllib.parse.quote(query)
    return f"https://news.google.com/rss/search?q={encoded_query}&hl=en-IN&gl=IN&ceid=IN:en"

def fetch_and_process_news(db):
    print("Starting news ingestion process...")
    for district, coords in DISTRICTS.items():
        print(f"Ingesting news for {district}...")
        for keyword in KEYWORDS:
            query = f"{district} {keyword}"
            rss_url = generate_google_news_rss_url(query)
            feed = feedparser.parse(rss_url)
            
            for entry in feed.entries[:3]:  # Process top 3 items per keyword to save AI API tokens
                # Title & link matching
                link = entry.link
                title = entry.title
                
                # Check for duplicates in DB
                existing = db.query(CivicRecord).filter(CivicRecord.link == link).first()
                if existing:
                    continue
                
                soup = BeautifulSoup(entry.summary, 'html.parser')
                summary = soup.get_text()
                
                # Create dedup hash
                dedup_str = f"{title}_{entry.get('published', '')}"
                dedup_hash = hashlib.md5(dedup_str.encode('utf-8')).hexdigest()
                
                existing_hash = db.query(CivicRecord).filter(CivicRecord.dedup_hash == dedup_hash).first()
                if existing_hash:
                    continue
                
                print(f"AI Analysing: {title}...")
                ai_data = classify_news_article(title, summary)
                if not ai_data:
                    continue
                
                # Map problem rate to severity level 1-5
                rate = ai_data.get("problem_rate", 50)
                severity = max(1, min(5, int(rate / 20) + 1))
                
                # Spatial point construction
                lat, lng = coords
                geom_wkt = f"POINT({lng} {lat})"
                
                # Store
                published_dt = datetime.utcnow()
                if 'published_parsed' in entry and entry.published_parsed:
                    published_dt = datetime(*entry.published_parsed[:6])
                
                record = CivicRecord(
                    id=f"news_{int(datetime.utcnow().timestamp())}_{hash(link) % 10000}",
                    title=title,
                    summary=summary,
                    category=ai_data.get("core_topic", "General"),
                    severity=severity,
                    latitude=lat,
                    longitude=lng,
                    geom=func.ST_GeomFromText(geom_wkt, 4326),
                    source="Google News RSS",
                    link=link,
                    upvotes=0,
                    lgd_code=None,  # Handled by LGD resolution
                    dedup_hash=dedup_hash,
                    published_at=published_dt,
                    scraped_at=datetime.utcnow(),
                    expires_at=datetime.utcnow() + timedelta(days=30)  # Expires in 30 days
                )
                
                try:
                    db.add(record)
                    db.commit()
                    print(f"-> Inserted issue: {record.title} (Severity: {record.severity})")
                except Exception as ex:
                    print(f"Failed to insert record: {ex}")
                    db.rollback()

if __name__ == "__main__":
    db = SessionLocal()
    try:
        fetch_and_process_news(db)
    finally:
        db.close()
