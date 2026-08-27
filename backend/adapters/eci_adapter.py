import re
import logging
import requests
from bs4 import BeautifulSoup
from typing import List
from backend.core.models import ExtractedAssignment, ExtractedPolitician, ExtractedPosition

logger = logging.getLogger(__name__)

def clean_name(raw: str) -> str:
    # Remove citations like [1], [a], (LoP in...), etc.
    cleaned = re.sub(r'\[.*?\]', '', raw)
    cleaned = re.sub(r'\(.*?\)', '', cleaned)
    return cleaned.strip()

class ECIScraperAdapter:
    """Real source adapter for the Election Commission of India."""
    def __init__(self):
        self.source_url = "https://en.wikipedia.org/wiki/Election_Commission_of_India"
        self.source_type = "MEDIA"

    def fetch_data(self) -> List[ExtractedAssignment]:
        logger.info(f"Fetching ECI leadership from {self.source_url}")
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        
        response = requests.get(self.source_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        
        assignments = []
        
        for tr in soup.find_all('tr'):
            th = tr.find('th')
            td = tr.find('td')
            if not th or not td:
                continue
                
            header_text = th.get_text(strip=True).lower()
            if 'executive' in header_text or 'commissioner' in header_text:
                td_raw = td.get_text(strip=True)
                matches = re.findall(r'([A-Za-z\s\.]+?)(?:\[\d+\])?\s*,\s*(Chief Election Commissioner of India|Election Commissioner of India)', td_raw)
                for name_raw, title_raw in matches:
                    name = clean_name(name_raw)
                    title = clean_name(title_raw)
                    if len(name) < 3:
                        continue
                        
                    pos = ExtractedPosition(
                        party_short_name="GOV",
                        organization_unit_type="NATIONAL",
                        official_title=title,
                        hierarchy_level=1,
                        position_type="CONSTITUTIONAL_BODY"
                    )
                    pol = ExtractedPolitician(
                        name=name,
                        party_short_name="GOV",
                        stable_id=f"eci_{name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="Election Commission of India Leadership"
                    ))
                    
        logger.info(f"ECIScraperAdapter extracted {len(assignments)} records")
        return assignments
