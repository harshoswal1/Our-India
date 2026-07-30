import datetime
from sqlalchemy import Column, String, Integer, Float, DateTime, Boolean, ForeignKey, Table, Text
from sqlalchemy.orm import relationship
from geoalchemy2 import Geometry
from pgvector.sqlalchemy import Vector
from database import Base

class User(Base):
    __tablename__ = "user_profiles"
    
    id = Column(String(50), primary_key=True, index=True)
    email = Column(String(100), unique=True, index=True, nullable=False)
    subscription_type = Column(String(20), default="free")
    fcm_token = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    
    grievances = relationship("Grievance", back_populates="user")
    audit_logs = relationship("AuditLog", back_populates="user")

class Grievance(Base):
    __tablename__ = "grievances"
    
    id = Column(String(50), primary_key=True, index=True)
    user_id = Column(String(50), ForeignKey("user_profiles.id"), nullable=False)
    category = Column(String(50), nullable=False)
    description = Column(Text, nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    # Spatial point geometry column (SRID 4326 for WGS 84 lat/lng)
    geom = Column(Geometry(geometry_type='POINT', srid=4326), nullable=True)
    media_url = Column(String(255), nullable=True)
    status = Column(String(30), default="SUBMITTED") # SUBMITTED, VERIFIED, IN_PROGRESS, RESOLVED, REJECTED
    assigned_department = Column(String(100), nullable=True)
    upvotes = Column(Integer, default=0)
    lgd_code = Column(String(50), nullable=True)
    is_local_draft = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)
    
    user = relationship("User", back_populates="grievances")

class CivicRecord(Base):
    """Stores AI-processed civic issues scraped from news feeds"""
    __tablename__ = "civic_records"
    
    id = Column(String(50), primary_key=True, index=True)
    title = Column(String(255), nullable=False)
    summary = Column(Text, nullable=False)
    category = Column(String(50), nullable=False)
    severity = Column(Integer, default=1)  # 1 to 5
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    geom = Column(Geometry(geometry_type='POINT', srid=4326), nullable=True)
    source = Column(String(100), nullable=False)
    link = Column(String(512), unique=True, nullable=False)
    upvotes = Column(Integer, default=0)
    lgd_code = Column(String(50), nullable=True)
    dedup_hash = Column(String(64), unique=True, index=True, nullable=False)
    published_at = Column(DateTime, nullable=True)
    scraped_at = Column(DateTime, default=datetime.datetime.utcnow)
    expires_at = Column(DateTime, nullable=False)

class LgdJurisdiction(Base):
    """Hierarchical spatial polygons for LGD (state -> district -> sub-district -> ward)"""
    __tablename__ = "lgd_jurisdictions"
    
    lgd_code = Column(String(50), primary_key=True, index=True)
    name = Column(String(150), nullable=False)
    level = Column(String(30), nullable=False) # STATE, DISTRICT, SUB_DISTRICT, ULB, WARD
    parent_code = Column(String(50), nullable=True)
    # Polygon boundary
    boundary = Column(Geometry(geometry_type='POLYGON', srid=4326), nullable=True)

class AreaLeader(Base):
    """Political representatives matching constituencies and LGDs"""
    __tablename__ = "area_leaders"
    
    id = Column(String(50), primary_key=True, index=True)
    name = Column(String(100), nullable=False)
    party = Column(String(100), nullable=False)
    role = Column(String(50), nullable=False) # MP, MLA, Corporator, Mayor, Commissioner
    constituency = Column(String(150), nullable=False)
    lgd_code = Column(String(50), nullable=True)
    contact_phone = Column(String(20), nullable=True)
    contact_email = Column(String(100), nullable=True)
    photo_url = Column(String(255), nullable=True)
    last_updated = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

class AuditLog(Base):
    __tablename__ = "audit_logs"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(50), ForeignKey("user_profiles.id"), nullable=True)
    action = Column(String(100), nullable=False) # READ, WRITE, LOGIN, EXPORT
    resource_id = Column(String(100), nullable=True)
    ip_address = Column(String(45), nullable=True)
    user_agent = Column(String(255), nullable=True)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)
    
    user = relationship("User", back_populates="audit_logs")

class LegalDocument(Base):
    __tablename__ = "legal_documents"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String(255), nullable=False)
    content = Column(Text, nullable=False)
    citations = Column(Text, nullable=True) # JSON or text list of references
    embedding = Column(Vector(3072), nullable=True) # 3072-dim from Gemini gemini-embedding-2

