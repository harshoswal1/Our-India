from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field

class ExtractedPolitician(BaseModel):
    name: str = Field(description="Normalized name of the politician without honorifics")
    party_id: str
    stable_id: Optional[str] = Field(default=None, description="Stable identifier from the source if available")
    photo_url: Optional[str] = None

class ExtractedPosition(BaseModel):
    party_id: str
    organization_unit_type: str = Field(description="E.g., NATIONAL, STATE, DISTRICT")
    official_title: str
    hierarchy_level: int
    position_type: str = "PARTY_ORGANIZATIONAL"

class ExtractedAssignment(BaseModel):
    position_ref: ExtractedPosition
    politician_ref: Optional[ExtractedPolitician] = None
    state: Optional[str] = None
    district: Optional[str] = None
    source_url: str
    source_type: str = Field(description="GOVERNMENT | PARTY_WEBSITE | MEDIA")
