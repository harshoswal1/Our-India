import os
import logging
from typing import List, Optional
from supabase import create_client, Client
from backend.core.models import ExtractedAssignment, ExtractedPolitician

logger = logging.getLogger(__name__)

class DatabaseClient:
    def __init__(self):
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            raise ValueError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set.")
        self.supabase: Client = create_client(url, key)

    def upsert_politician(self, pol: ExtractedPolitician) -> Optional[str]:
        # Deduplication strategy: Use stable_id if available, else fallback to name + party_id
        # We rely on Supabase returning the generated UUID.
        
        # 1. Try to find existing
        query = self.supabase.table("politicians").select("id").eq("party_id", pol.party_id)
        if pol.stable_id:
            query = query.eq("stable_id", pol.stable_id)
        else:
            query = query.eq("name", pol.name)
            
        result = query.execute()
        
        if result.data and len(result.data) > 0:
            pol_id = result.data[0]['id']
            # Update existing
            self.supabase.table("politicians").update({
                "name": pol.name,
                "photo": pol.photo_url,
                "status": "ACTIVE"
            }).eq("id", pol_id).execute()
            return pol_id
        else:
            # Insert new
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

    def upsert_assignment(self, assignment: ExtractedAssignment):
        try:
            # 1. Resolve Organization Unit ID
            # In a real implementation, we query `political_organization_units` using state/district.
            # For this prototype, we'll assume a dummy UUID or fetch it.
            org_unit_id = "00000000-0000-0000-0000-000000000001" 
            
            # 2. Upsert Position (relies on UNIQUE constraint party_id, organization_unit_type, official_title)
            pos_data = {
                "party_id": assignment.position_ref.party_id,
                "organization_unit_type": assignment.position_ref.organization_unit_type,
                "position_name": assignment.position_ref.official_title,
                "official_title": assignment.position_ref.official_title,
                "hierarchy_level": assignment.position_ref.hierarchy_level,
                "position_type": assignment.position_ref.position_type,
                "verification_status": "VERIFIED" if assignment.source_type == "GOVERNMENT" else "PENDING"
            }
            
            pos_res = self.supabase.table("political_positions").upsert(
                pos_data, 
                on_conflict="party_id, organization_unit_type, official_title"
            ).execute()
            
            if not pos_res.data:
                logger.error("Failed to upsert position")
                return
            
            position_id = pos_res.data[0]['id']
            
            # 3. Upsert Politician (if present)
            politician_id = None
            if assignment.politician_ref:
                politician_id = self.upsert_politician(assignment.politician_ref)
                
            # 4. Handle Assignment History
            # Check who is currently active in this position
            active_assignments = self.supabase.table("political_position_assignments") \
                .select("id, politician_id") \
                .eq("position_id", position_id) \
                .eq("is_active", True) \
                .execute()
                
            if active_assignments.data:
                current = active_assignments.data[0]
                if current['politician_id'] == politician_id:
                    # Same person, do nothing
                    return
                else:
                    # Holder changed! Deactivate old assignment.
                    self.supabase.table("political_position_assignments") \
                        .update({"is_active": False, "effective_to": "now()"}) \
                        .eq("id", current['id']) \
                        .execute()
            
            # 5. Insert new active assignment
            new_assignment = {
                "party_id": assignment.position_ref.party_id,
                "politician_id": politician_id,
                "position_id": position_id,
                "organization_unit_id": org_unit_id,
                "is_active": True,
                "verification_status": pos_data["verification_status"]
            }
            self.supabase.table("political_position_assignments").insert(new_assignment).execute()
            logger.info(f"Successfully upserted assignment for {assignment.position_ref.official_title}")
            
        except Exception as e:
            logger.error(f"Safe failure: Could not upsert assignment. Reason: {str(e)}")
