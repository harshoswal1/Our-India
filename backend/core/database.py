import os
import uuid
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple, Any
from supabase import create_client, Client
from backend.core.models import ExtractedAssignment, ExtractedPolitician

logger = logging.getLogger(__name__)

# Deterministic namespace UUID for stable IDs across all ingestion runs
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
        
        masked_key = key[:6] + "..." + key[-4:] if len(key) > 10 else "***"
        logger.info(f"Connecting to Supabase at: {url} with service_role key: {masked_key}")
        self.supabase: Client = create_client(url, key)
        self.party_map: Dict[str, str] = {}
        self.unit_map: Dict[str, str] = {}
        self.ensure_core_entities()

    def _execute_upsert(self, table_name: str, payload: dict, on_conflict: str = "id") -> dict:
        """Executes an upsert operation on Supabase with explicit error reporting."""
        try:
            res = self.supabase.table(table_name).upsert(payload, on_conflict=on_conflict).execute()
            if res.data and len(res.data) > 0:
                return res.data[0]
            return payload
        except Exception as e:
            logger.error(f"❌ Supabase UPSERT Error on table '{table_name}': {str(e)} | Payload: {payload}")
            # Try insert if upsert with on_conflict is not supported for this table
            try:
                res = self.supabase.table(table_name).insert(payload).execute()
                if res.data and len(res.data) > 0:
                    return res.data[0]
                return payload
            except Exception as ex2:
                logger.error(f"❌ Supabase INSERT fallback also failed on table '{table_name}': {str(ex2)}")
                raise ex2

    def ensure_core_entities(self):
        """Deterministically verifies and writes canonical parties and organization units."""
        logger.info("⚡ Seeding and verifying core parties & organizational units in Supabase...")
        
        for p in CORE_PARTIES:
            party_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"party_{p['short_name']}"))
            party_payload = {
                "id": party_uuid,
                "name": p["name"],
                "short_name": p["short_name"],
                "symbol": p["symbol"],
                "status": "ACTIVE"
            }
            
            # 1. Upsert Party
            inserted_party = self._execute_upsert("parties", party_payload, on_conflict="id")
            party_id = inserted_party.get("id") or party_uuid
            self.party_map[p["short_name"]] = party_id

            # 2. Upsert National Organization Unit for this party
            unit_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"unit_national_{p['short_name']}"))
            unit_payload = {
                "id": unit_uuid,
                "party_id": party_id,
                "official_name": f"{p['short_name']} National Committee",
                "unit_type": "NATIONAL",
                "hierarchy_level": 1,
                "geographic_scope": "NATIONAL",
                "status": "ACTIVE"
            }
            
            inserted_unit = self._execute_upsert("political_organization_units", unit_payload, on_conflict="id")
            unit_id = inserted_unit.get("id") or unit_uuid
            self.unit_map[f"{p['short_name']}_NATIONAL"] = unit_id

        # Verify immediate write
        try:
            res = self.supabase.table("parties").select("*", count="exact").execute()
            party_count = res.count if res.count is not None else len(res.data)
            logger.info(f"✅ Core entity seeding verified: {party_count} parties present in Supabase.")
            if party_count == 0:
                raise RuntimeError("Failed to seed parties table: table remains empty after upsert!")
        except Exception as e:
            logger.error(f"❌ Error verifying parties after seeding: {e}")
            raise e

    def register_source(self, source_url: str, source_type: str, content_hash: Optional[str] = None) -> str:
        """Tracks the source in source_registry table, returns source_id UUID"""
        source_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"src_{source_url}"))
        authority = when_source_map.get(source_type.upper(), "LEVEL_3")
        now_iso = datetime.now(timezone.utc).isoformat()
        
        source_payload = {
            "source_id": source_uuid,
            "source_name": source_url.split("//")[-1].split("/")[0],
            "url": source_url,
            "source_type": source_type,
            "authority_level": authority,
            "last_checked": now_iso,
            "content_hash": content_hash,
            "status": "ACTIVE"
        }
        
        try:
            self._execute_upsert("source_registry", source_payload, on_conflict="source_id")
            return source_uuid
        except Exception:
            # Fallback if primary key is named id
            try:
                alt_payload = dict(source_payload)
                alt_payload["id"] = source_uuid
                self._execute_upsert("source_registry", alt_payload, on_conflict="id")
                return source_uuid
            except Exception as e:
                logger.warning(f"Source registry notice: {e}")
                return source_uuid

    def upsert_politician(self, pol: ExtractedPolitician, party_id: str) -> str:
        """
        Deduplicates politician by deterministic UUID based on party_id + normalized name.
        """
        pol_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"pol_{party_id}_{pol.name}"))
        new_data = {
            "id": pol_uuid,
            "name": pol.name,
            "photo": pol.photo_url,
            "biography": pol.biography,
            "education": pol.education,
            "status": "ACTIVE"
        }
        
        inserted = self._execute_upsert("politicians", new_data, on_conflict="id")
        return inserted.get("id") or pol_uuid

    def upsert_assignment(self, assignment: ExtractedAssignment) -> Tuple[bool, bool, bool]:
        """
        Upserts position, politician, active assignment, and records verification.
        Returns (position_success, politician_success, history_preserved).
        """
        party_short = assignment.position_ref.party_short_name
        party_id = self.party_map.get(party_short) or str(uuid.uuid5(PARTY_NAMESPACE, f"party_{party_short}"))
        unit_key = f"{party_short}_{assignment.position_ref.organization_unit_type}"
        org_unit_id = self.unit_map.get(unit_key) or str(uuid.uuid5(PARTY_NAMESPACE, f"unit_{party_short}"))

        source_id = self.register_source(assignment.source_url, assignment.source_type)
        verification_status, confidence = calculate_verification_metrics(assignment.source_type)

        # 1. Upsert Position (Deterministic UUID)
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
        
        self._execute_upsert("political_positions", pos_data, on_conflict="id")
        position_id = pos_uuid
        pos_ok = True
        
        # 2. Upsert Politician (if present)
        politician_id = None
        pol_ok = False
        if assignment.politician_ref:
            politician_id = self.upsert_politician(assignment.politician_ref, party_id)
            if politician_id:
                pol_ok = True
            
        # 3. Handle Assignment History & Deduplication
        now_iso = datetime.now(timezone.utc).isoformat()
        assignment_id = None
        hist_preserved = False

        try:
            active_assignments = self.supabase.table("political_position_assignments") \
                .select("id, politician_id") \
                .eq("position_id", position_id) \
                .eq("is_active", True) \
                .execute()
                
            if active_assignments.data and len(active_assignments.data) > 0:
                current = active_assignments.data[0]
                if current.get('politician_id') == politician_id:
                    assignment_id = current['id']
                else:
                    # Holder changed! Mark old assignment inactive
                    self.supabase.table("political_position_assignments") \
                        .update({"is_active": False, "effective_to": now_iso}) \
                        .eq("id", current['id']) \
                        .execute()
                    hist_preserved = True
                    
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
                    self._execute_upsert("political_position_assignments", new_assignment, on_conflict="id")
                    assignment_id = assign_uuid
            else:
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
                self._execute_upsert("political_position_assignments", new_assignment, on_conflict="id")
                assignment_id = assign_uuid
        except Exception as e:
            logger.error(f"❌ Error managing assignment for position {assignment.position_ref.official_title}: {e}")
            raise e

        # 4. Insert Verification Record
        if source_id and assignment_id:
            ver_uuid = str(uuid.uuid5(PARTY_NAMESPACE, f"ver_{assignment_id}_{source_id}"))
            ver_record = {
                "id": ver_uuid,
                "source_id": source_id,
                "verification_status": verification_status,
                "verification_timestamp": now_iso,
                "confidence": confidence,
                "reviewer": "AUTONOMOUS_INGESTION_WORKER",
                "notes": assignment.evidence_notes or f"Auto-verified via {assignment.source_type} adapter"
            }
            try:
                self._execute_upsert("verification_records", ver_record, on_conflict="id")
            except Exception as ex:
                logger.warning(f"Verification record notice: {ex}")

        return pos_ok, pol_ok, hist_preserved

    def verify_final_database_counts(self) -> Dict[str, int]:
        """Queries actual production Supabase tables with select('*', count='exact') to avoid column mismatch."""
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
                res = self.supabase.table(tbl).select("*", count="exact").execute()
                counts[tbl] = res.count if res.count is not None else len(res.data)
            except Exception as e:
                logger.error(f"❌ Error fetching count for table '{tbl}': {e}")
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
