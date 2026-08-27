import os
import hashlib
import logging
from datetime import datetime, timezone
from typing import List, Optional, Tuple
from supabase import create_client, Client
from backend.core.models import ExtractedAssignment, ExtractedPolitician

logger = logging.getLogger(__name__)

class DatabaseClient:
    def __init__(self):
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            raise ValueError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in environment.")
        self.supabase: Client = create_client(url, key)

    def register_source(self, source_url: str, source_type: str, content_hash: Optional[str] = None) -> Optional[str]:
        """Tracks the source in source_registry table, returns source_id UUID"""
        try:
            authority = when_source_authority(source_type)
            now_iso = datetime.now(timezone.utc).isoformat()
            
            # Check if source already registered
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
            logger.warning(f"Failed to register source: {e}")
            return None

    def upsert_politician(self, pol: ExtractedPolitician) -> Optional[str]:
        """
        Deduplication strategy:
        1. Query by stable_id if provided.
        2. Fallback to (name + party_id).
        """
        try:
            query = self.supabase.table("politicians").select("id").eq("party_id", pol.party_id)
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
                    "party_id": pol.party_id,
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

    def upsert_assignment(self, assignment: ExtractedAssignment):
        """
        Idempotently and safely upserts position and assignment.
        Preserves history: if holder changes, marks previous assignment inactive with effective_to=now().
        """
        try:
            source_id = self.register_source(assignment.source_url, assignment.source_type)
            verification_status, confidence = calculate_verification_metrics(assignment.source_type)

            org_unit_id = "00000000-0000-0000-0000-000000000001"
            
            # 1. Upsert Position
            pos_data = {
                "party_id": assignment.position_ref.party_id,
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
            
            if not pos_res.data:
                logger.error("Failed to upsert position")
                return
            
            position_id = pos_res.data[0]['id']
            
            # 2. Upsert Politician (if holder is known)
            politician_id = None
            if assignment.politician_ref:
                politician_id = self.upsert_politician(assignment.politician_ref)
                
            # 3. Handle Assignment History & Deduplication
            active_assignments = self.supabase.table("political_position_assignments") \
                .select("id, politician_id") \
                .eq("position_id", position_id) \
                .eq("is_active", True) \
                .execute()
                
            now_iso = datetime.now(timezone.utc).isoformat()

            if active_assignments.data and len(active_assignments.data) > 0:
                current = active_assignments.data[0]
                if current['politician_id'] == politician_id:
                    # Idempotent: exact same active holder. Just touch verification.
                    assignment_id = current['id']
                else:
                    # Holder changed! Historical preservation rule:
                    # Mark previous assignment as inactive with effective_to populated
                    self.supabase.table("political_position_assignments") \
                        .update({"is_active": False, "effective_to": now_iso}) \
                        .eq("id", current['id']) \
                        .execute()
                    
                    # Insert new active assignment
                    new_assignment = {
                        "party_id": assignment.position_ref.party_id,
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
                # No active assignment exists. Create new active assignment.
                new_assignment = {
                    "party_id": assignment.position_ref.party_id,
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

            # 4. Insert Verification Record if source is registered
            if source_id and assignment_id:
                ver_record = {
                    "record_id": assignment_id,
                    "source_id": source_id,
                    "verification_status": verification_status,
                    "verification_timestamp": now_iso,
                    "confidence": confidence,
                    "reviewer": "AUTONOMOUS_INGESTION_WORKER",
                    "notes": f"Auto-verified via {assignment.source_type} adapter"
                }
                self.supabase.table("verification_records").insert(ver_record).execute()

            logger.info(f"Successfully processed assignment: {assignment.position_ref.official_title} -> {assignment.politician_ref.name if assignment.politician_ref else 'Vacant / Not fetched'}")
            
        except Exception as e:
            # Source Safety Rule: Individual error will NOT wipe database
            logger.error(f"Safe failure for assignment {assignment.position_ref.official_title}: {str(e)}")

def when_source_authority(source_type: str) -> str:
    return when_source_map.get(source_type.upper(), "LEVEL_3")

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
