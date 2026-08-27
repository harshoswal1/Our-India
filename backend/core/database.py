import os
import uuid
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple, Any
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
    {"name": "Election Commission & Constitutional Bodies", "short_name": "GOV", "symbol": "Ashoka Emblem", "party_type": "CONSTITUTIONAL"}
]

class DatabaseClient:
    def __init__(self):
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            raise ValueError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in environment.")
        
        logger.info(f"Initializing Supabase client with endpoint: {url.split('//')[-1].split('/')[0]}")
        self.supabase: Client = create_client(url, key)
        self.party_map: Dict[str, str] = {}
        self.unit_map: Dict[str, str] = {}
        self.ensure_core_entities()

    def _execute_safe_upsert(self, table_name: str, payload: dict, on_conflict: Optional[str] = None) -> Optional[dict]:
        """Helper to execute insert/upsert with fallback and detailed error reporting."""
        try:
            if on_conflict:
                res = self.supabase.table(table_name).upsert(payload, on_conflict=on_conflict).execute()
            else:
                res = self.supabase.table(table_name).insert(payload).execute()
                
            if res.data and len(res.data) > 0:
                return res.data[0]
            return None
        except Exception as e:
            # Try plain insert if on_conflict syntax fails
            try:
                res = self.supabase.table(table_name).insert(payload).execute()
                if res.data and len(res.data) > 0:
                    return res.data[0]
            except Exception as ex2:
                logger.error(f"❌ Database error on table '{table_name}': {str(ex2)}")
                raise ex2
            return None

    def ensure_core_entities(self):
        """Ensures canonical parties and organization units exist in Supabase so foreign keys succeed."""
        logger.info("Verifying and seeding core parties and organizational units in Supabase...")
        
        for p in CORE_PARTIES:
            party_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"party_{p['short_name']}"))
            party_payload = {
                "id": party_uuid,
                "name": p["name"],
                "official_name": p["name"],
                "short_name": p["short_name"],
                "symbol": p["symbol"],
                "color": "#FF9933",
                "founded_year": 1980,
                "headquarters": "New Delhi, India",
                "status": "ACTIVE",
                "is_active": True
            }
            
            # Check if party exists
            try:
                q = self.supabase.table("parties").select("id").eq("short_name", p["short_name"]).execute()
                if q.data and len(q.data) > 0:
                    self.party_map[p["short_name"]] = q.data[0]["id"]
                else:
                    # Clean payload for parties table columns
                    inserted = self._execute_safe_upsert("parties", {
                        "id": party_uuid,
                        "name": p["name"],
                        "short_name": p["short_name"],
                        "symbol": p["symbol"],
                        "status": "ACTIVE"
                    }, on_conflict="short_name")
                    self.party_map[p["short_name"]] = inserted["id"] if inserted else party_uuid
            except Exception as e:
                logger.warning(f"Notice on seeding party '{p['short_name']}': {e}")
                self.party_map[p["short_name"]] = party_uuid

            # Ensure National Organization Unit for this party
            actual_party_id = self.party_map[p["short_name"]]
            unit_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"unit_national_{p['short_name']}"))
            unit_payload = {
                "id": unit_uuid,
                "party_id": actual_party_id,
                "unit_name": f"{p['short_name']} National Committee",
                "official_name": f"{p['short_name']} National Committee",
                "unit_type": "NATIONAL",
                "hierarchy_level": 1,
                "geographic_scope": "NATIONAL",
                "status": "ACTIVE",
                "is_active": True
            }
            
            try:
                uq = self.supabase.table("political_organization_units").select("id").eq("party_id", actual_party_id).eq("unit_type", "NATIONAL").execute()
                if uq.data and len(uq.data) > 0:
                    self.unit_map[f"{p['short_name']}_NATIONAL"] = uq.data[0]["id"]
                else:
                    inserted_unit = self._execute_safe_upsert("political_organization_units", {
                        "id": unit_uuid,
                        "party_id": actual_party_id,
                        "official_name": f"{p['short_name']} National Committee",
                        "unit_type": "NATIONAL",
                        "hierarchy_level": 1,
                        "geographic_scope": "NATIONAL",
                        "status": "ACTIVE"
                    }, on_conflict="id")
                    self.unit_map[f"{p['short_name']}_NATIONAL"] = inserted_unit["id"] if inserted_unit else unit_uuid
            except Exception as e:
                logger.warning(f"Notice on seeding org unit for '{p['short_name']}': {e}")
                self.unit_map[f"{p['short_name']}_NATIONAL"] = unit_uuid

        logger.info(f"✅ Core entity verification complete. Party mapping active for {len(self.party_map)} parties.")

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
                source_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"src_{source_url}"))
                new_source = {
                    "source_id": source_uuid,
                    "source_name": source_url.split("//")[-1].split("/")[0],
                    "url": source_url,
                    "source_type": source_type,
                    "authority_level": authority,
                    "last_checked": now_iso,
                    "content_hash": content_hash,
                    "status": "ACTIVE"
                }
                insert_res = self.supabase.table("source_registry").insert(new_source).execute()
                if insert_res.data and len(insert_res.data) > 0:
                    return insert_res.data[0].get("source_id") or source_uuid
                return source_uuid
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
                pol_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"pol_{party_id}_{pol.name}"))
                new_data = {
                    "id": pol_uuid,
                    "name": pol.name,
                    "party_id": party_id,
                    "photo": pol.photo_url,
                    "stable_id": pol.stable_id,
                    "status": "ACTIVE"
                }
                res = self.supabase.table("politicians").insert(new_data).execute()
                if res.data and len(res.data) > 0:
                    return res.data[0]['id']
                return pol_uuid
        except Exception as e:
            logger.error(f"Error upserting politician {pol.name}: {e}")
            raise e

    def upsert_assignment(self, assignment: ExtractedAssignment) -> Tuple[bool, bool, bool]:
        """
        Upserts position, politician, active assignment, and records verification.
        Returns (position_success, politician_success, history_preserved).
        """
        party_short = assignment.position_ref.party_short_name
        party_id = self.party_map.get(party_short)
        if not party_id:
            party_id = str(uuid.uuid5(PARTY_NAMESPACE, f"party_{party_short}"))
            self.party_map[party_short] = party_id
            
        unit_key = f"{party_short}_{assignment.position_ref.organization_unit_type}"
        org_unit_id = self.unit_map.get(unit_key)
        if not org_unit_id:
            org_unit_id = str(uuid.uuid5(PARTY_NAMESPACE, f"unit_{party_short}"))
            self.unit_map[unit_key] = org_unit_id

        source_id = self.register_source(assignment.source_url, assignment.source_type)
        verification_status, confidence = calculate_verification_metrics(assignment.source_type)

        # 1. Upsert Position
        pos_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"pos_{party_id}_{assignment.position_ref.official_title}"))
        pos_data = {
            "id": pos_uuid,
            "party_id": party_id,
            "organization_unit_type": assignment.position_ref.organization_unit_type,
            "position_name": assignment.position_ref.official_title,
            "official_title": assignment.position_ref.official_title,
            "hierarchy_level": assignment.position_ref.hierarchy_level,
            "position_type": assignment.position_ref.position_type,
            "source_id": source_id,
            "verification_status": verification_status
        }
        
        pos_res = self.supabase.table("political_positions").upsert(
            pos_data, 
            on_conflict="party_id, organization_unit_type, official_title"
        ).execute()
        
        position_id = pos_uuid
        if pos_res.data and len(pos_res.data) > 0:
            position_id = pos_res.data[0]['id']
        
        pos_ok = True
        
        # 2. Upsert Politician (if present)
        politician_id = None
        pol_ok = False
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
        hist_preserved = False

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
                assign_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"assign_{position_id}_{politician_id}_{now_iso}"))
                new_assignment = {
                    "id": assign_uuid,
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
                assignment_id = ins_res.data[0]['id'] if ins_res.data else assign_uuid
        else:
            # No existing active assignment
            assign_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"assign_{position_id}_{politician_id}"))
            new_assignment = {
                "id": assign_uuid,
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
            assignment_id = ins_res.data[0]['id'] if ins_res.data else assign_uuid

        # 4. Insert Verification Record
        if source_id and assignment_id:
            ver_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"ver_{assignment_id}_{source_id}"))
            ver_record = {
                "id": ver_uuid,
                "record_id": assignment_id,
                "source_id": source_id,
                "verification_status": verification_status,
                "verification_timestamp": now_iso,
                "confidence": confidence,
                "reviewer": "AUTONOMOUS_INGESTION_WORKER",
                "notes": assignment.evidence_notes or f"Auto-verified via {assignment.source_type} adapter"
            }
            try:
                self.supabase.table("verification_records").upsert(ver_record, on_conflict="id").execute()
            except Exception:
                try:
                    self.supabase.table("verification_records").insert(ver_record).execute()
                except Exception:
                    pass

        return pos_ok, pol_ok, hist_preserved

    def verify_final_database_counts(self) -> Dict[str, int]:
        """Queries actual production Supabase tables to verify non-zero counts."""
        tables = [
            "parties",
            "political_organization_units",
            "political_positions",
            "politicians",
            "political_position_assignments",
            "source_registry",
            "verification_records"
        ]
        
        counts = {}
        for tbl in tables:
            try:
                res = self.supabase.table(tbl).select("id", count="exact").execute()
                counts[tbl] = res.count if res.count is not None else len(res.data)
            except Exception as e:
                logger.warning(f"Could not fetch count for '{tbl}': {e}")
                counts[tbl] = -1
                
        return counts

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
