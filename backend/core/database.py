import os
import uuid
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple
from supabase import create_client, Client
from backend.core.models import ExtractedAssignment, ExtractedPolitician

logger = logging.getLogger(__name__)

# Deterministic namespace UUID for stable IDs
PARTY_NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

CORE_PARTIES = [
    {"name": "Bharatiya Janata Party", "short_name": "BJP", "symbol": "Lotus", "party_type": "NATIONAL"},
    {"name": "Indian National Congress", "short_name": "INC", "symbol": "Hand", "party_type": "NATIONAL"},
    {"name": "Aam Aadmi Party", "short_name": "AAP", "symbol": "Broom", "party_type": "NATIONAL"},
    {"name": "Bahujan Samaj Party", "short_name": "BSP", "symbol": "Elephant", "party_type": "NATIONAL"},
    {"name": "Communist Party of India (Marxist)", "short_name": "CPI(M)", "symbol": "Hammer and Sickle", "party_type": "NATIONAL"},
    {"name": "National People's Party", "short_name": "NPP", "symbol": "Book", "party_type": "NATIONAL"},
    {"name": "All India Trinamool Congress", "short_name": "TMC", "symbol": "Flowers & Grass", "party_type": "STATE_REGIONAL"},
    {"name": "Dravida Munnetra Kazhagam", "short_name": "DMK", "symbol": "Rising Sun", "party_type": "STATE_REGIONAL"},
    {"name": "Government & Constitutional Bodies", "short_name": "GOV", "symbol": "Ashoka Emblem", "party_type": "CONSTITUTIONAL"}
]

class DatabaseClient:
    def __init__(self):
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            raise ValueError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in environment.")
        self.supabase: Client = create_client(url, key)
        self.party_map: Dict[str, str] = {}
        self.unit_map: Dict[str, str] = {}
        self.ensure_core_entities()

    def ensure_core_entities(self):
        """Ensures canonical parties and organization units exist in Supabase so foreign keys succeed."""
        logger.info("Verifying and seeding core parties and organizational units...")
        try:
            for p in CORE_PARTIES:
                party_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"party_{p['short_name']}"))
                party_data = {
                    "id": party_uuid,
                    "name": p["name"],
                    "short_name": p["short_name"],
                    "symbol": p["symbol"],
                    "party_type": p["party_type"],
                    "is_active": True
                }
                # Upsert into parties table
                try:
                    self.supabase.table("parties").upsert(party_data, on_conflict="short_name").execute()
                except Exception as ex:
                    # Fallback to id on_conflict if short_name unique constraint differs
                    try:
                        self.supabase.table("parties").upsert(party_data, on_conflict="id").execute()
                    except Exception:
                        pass
                
                self.party_map[p["short_name"]] = party_uuid

                # Ensure National Organization Unit for this party
                unit_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"unit_national_{p['short_name']}"))
                unit_data = {
                    "id": unit_uuid,
                    "party_id": party_uuid,
                    "unit_name": f"{p['short_name']} National Committee",
                    "unit_type": "NATIONAL",
                    "state": None,
                    "district": None,
                    "is_active": True
                }
                try:
                    self.supabase.table("political_organization_units").upsert(unit_data, on_conflict="id").execute()
                except Exception:
                    pass
                self.unit_map[f"{p['short_name']}_NATIONAL"] = unit_uuid

            logger.info(f"Core parties and organization units verified. {len(self.party_map)} parties active.")
        except Exception as e:
            logger.warning(f"Core entity initialization encountered notice: {e}")

    def register_source(self, source_url: str, source_type: str, content_hash: Optional[str] = None) -> Optional[str]:
        """Tracks the source in source_registry table, returns source_id UUID"""
        try:
            authority = when_source_map.get(source_type.upper(), "LEVEL_3")
            now_iso = datetime.now(timezone.utc).isoformat()
            
            res = self.supabase.table("source_registry").select("source_id").eq("url", source_url).execute()
            if res.data and len(res.data) > 0:
                source_id = res.data[0]["source_id"]
                self.supabase.table("source_registry").update({
                    "last_checked": now_iso,
                    "content_hash": content_hash,
                    "status": "ACTIVE"
                }).eq("source_id", source_id).execute()
                return source_id
            else:
                new_source = {
                    "source_name": source_url.split("//")[-1].split("/")[0],
                    "url": source_url,
                    "source_type": source_type,
                    "authority_level": authority,
                    "last_checked": now_iso,
                    "content_hash": content_hash,
                    "status": "ACTIVE"
                }
                insert_res = self.supabase.table("source_registry").insert(new_source).execute()
                if insert_res.data:
                    return insert_res.data[0]["source_id"]
                return None
        except Exception as e:
            logger.warning(f"Source registry notice: {e}")
            return None

    def upsert_politician(self, pol: ExtractedPolitician, party_id: str) -> Optional[str]:
        """
        Deduplicates politician by stable_id or name + party_id.
        """
        try:
            query = self.supabase.table("politicians").select("id").eq("party_id", party_id)
            if pol.stable_id:
                query = query.eq("stable_id", pol.stable_id)
            else:
                query = query.eq("name", pol.name)
                
            result = query.execute()
            
            if result.data and len(result.data) > 0:
                pol_id = result.data[0]['id']
                self.supabase.table("politicians").update({
                    "name": pol.name,
                    "photo": pol.photo_url,
                    "status": "ACTIVE"
                }).eq("id", pol_id).execute()
                return pol_id
            else:
                new_data = {
                    "name": pol.name,
                    "party_id": party_id,
                    "photo": pol.photo_url,
                    "stable_id": pol.stable_id,
                    "status": "ACTIVE"
                }
                res = self.supabase.table("politicians").insert(new_data).execute()
                if res.data:
                    return res.data[0]['id']
                return None
        except Exception as e:
            logger.error(f"Error upserting politician {pol.name}: {e}")
            return None

    def upsert_assignment(self, assignment: ExtractedAssignment) -> Tuple[bool, bool, bool]:
        """
        Upserts position, politician, active assignment, and records verification.
        Returns (position_created/updated, politician_created/updated, historical_preserved).
        """
        pos_ok = False
        pol_ok = False
        hist_preserved = False

        try:
            party_short = assignment.position_ref.party_short_name
            party_id = self.party_map.get(party_short) or str(uuid.uuid5(PARTY_NAMESPACE, f"party_{party_short}"))
            
            unit_key = f"{party_short}_{assignment.position_ref.organization_unit_type}"
            org_unit_id = self.unit_map.get(unit_key) or str(uuid.uuid5(PARTY_NAMESPACE, f"unit_{party_short}"))

            source_id = self.register_source(assignment.source_url, assignment.source_type)
            verification_status, confidence = calculate_verification_metrics(assignment.source_type)

            # 1. Upsert Position
            pos_data = {
                "party_id": party_id,
                "organization_unit_type": assignment.position_ref.organization_unit_type,
                "position_name": assignment.position_ref.official_title,
                "official_title": assignment.position_ref.official_title,
                "hierarchy_level": assignment.position_ref.hierarchy_level,
                "position_type": assignment.position_ref.position_type,
                "source_id": source_id,
                "verification_status": verification_status
            }
            
            try:
                pos_res = self.supabase.table("political_positions").upsert(
                    pos_data, 
                    on_conflict="party_id, organization_unit_type, official_title"
                ).execute()
            except Exception:
                pos_res = self.supabase.table("political_positions").insert(pos_data).execute()
            
            if not pos_res.data:
                logger.warning(f"Position upsert returned no data for {assignment.position_ref.official_title}")
                return False, False, False
            
            position_id = pos_res.data[0]['id']
            pos_ok = True
            
            # 2. Upsert Politician (if present)
            politician_id = None
            if assignment.politician_ref:
                politician_id = self.upsert_politician(assignment.politician_ref, party_id)
                if politician_id:
                    pol_ok = True
                
            # 3. Handle Assignment History & Deduplication
            active_assignments = self.supabase.table("political_position_assignments") \
                .select("id, politician_id") \
                .eq("position_id", position_id) \
                .eq("is_active", True) \
                .execute()
                
            now_iso = datetime.now(timezone.utc).isoformat()
            assignment_id = None

            if active_assignments.data and len(active_assignments.data) > 0:
                current = active_assignments.data[0]
                if current['politician_id'] == politician_id:
                    # Idempotent: same person currently holding position
                    assignment_id = current['id']
                else:
                    # Holder changed! Historical preservation:
                    # Mark old assignment inactive with effective_to populated
                    self.supabase.table("political_position_assignments") \
                        .update({"is_active": False, "effective_to": now_iso}) \
                        .eq("id", current['id']) \
                        .execute()
                    hist_preserved = True
                    
                    # Insert new active assignment
                    new_assignment = {
                        "party_id": party_id,
                        "politician_id": politician_id,
                        "position_id": position_id,
                        "organization_unit_id": org_unit_id,
                        "effective_from": now_iso,
                        "effective_to": None,
                        "is_active": True,
                        "verification_status": verification_status
                    }
                    ins_res = self.supabase.table("political_position_assignments").insert(new_assignment).execute()
                    assignment_id = ins_res.data[0]['id'] if ins_res.data else None
            else:
                # No existing active assignment
                new_assignment = {
                    "party_id": party_id,
                    "politician_id": politician_id,
                    "position_id": position_id,
                    "organization_unit_id": org_unit_id,
                    "effective_from": now_iso,
                    "effective_to": None,
                    "is_active": True,
                    "verification_status": verification_status
                }
                ins_res = self.supabase.table("political_position_assignments").insert(new_assignment).execute()
                assignment_id = ins_res.data[0]['id'] if ins_res.data else None

            # 4. Insert Verification Record
            if source_id and assignment_id:
                ver_record = {
                    "record_id": assignment_id,
                    "source_id": source_id,
                    "verification_status": verification_status,
                    "verification_timestamp": now_iso,
                    "confidence": confidence,
                    "reviewer": "AUTONOMOUS_INGESTION_WORKER",
                    "notes": assignment.evidence_notes or f"Auto-verified via {assignment.source_type} adapter"
                }
                try:
                    self.supabase.table("verification_records").insert(ver_record).execute()
                except Exception:
                    pass

            return pos_ok, pol_ok, hist_preserved
            
        except Exception as e:
            logger.error(f"Safe failure for assignment {assignment.position_ref.official_title}: {str(e)}")
            return False, False, False

when_source_map = {
    "GOVERNMENT": "LEVEL_1",
    "PARTY_WEBSITE": "LEVEL_2",
    "MEDIA": "LEVEL_3",
    "ACADEMIC": "LEVEL_2",
    "UNKNOWN": "LEVEL_4"
}

def calculate_verification_metrics(source_type: str) -> Tuple[str, float]:
    st = source_type.upper()
    if st == "GOVERNMENT":
        return "VERIFIED", 0.98
    elif st == "PARTY_WEBSITE":
        return "VERIFIED", 0.95
    elif st == "MEDIA":
        return "PENDING", 0.75
    else:
        return "PENDING", 0.50
