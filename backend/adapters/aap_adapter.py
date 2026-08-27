import re
import logging
import requests
from bs4 import BeautifulSoup
from typing import List
from backend.core.models import ExtractedAssignment, ExtractedPolitician, ExtractedPosition

logger = logging.getLogger(__name__)

def clean_text(raw: str) -> str:
    cleaned = re.sub(r'\[.*?\]', '', raw)
    cleaned = re.sub(r'\(.*?\)', '', cleaned)
    return cleaned.strip()

class AAPScraperAdapter:
    """Real source adapter for Aam Aadmi Party (AAP) national office-bearers."""
    def __init__(self):
        self.source_url = "https://en.wikipedia.org/wiki/Aam_Aadmi_Party"
        self.source_type = "MEDIA"

    def fetch_data(self) -> List[ExtractedAssignment]:
        logger.info(f"Fetching AAP leadership from {self.source_url}")
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        
        response = requests.get(self.source_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        
        assignments = []
        party_short = "AAP"

        for tr in soup.find_all('tr'):
            th = tr.find('th')
            td = tr.find('td')
            if not th or not td:
                continue

            th_text = th.get_text(strip=True).lower()

            # 1. National Convener / Leader
            if th_text in ['leader', 'national convener', 'convener']:
                leader_names = [a.get_text(strip=True) for a in td.find_all('a') if not a.get_text(strip=True).startswith('[')]
                if not leader_names:
                    leader_names = [td.get_text(strip=True)]
                    
                for name in leader_names:
                    clean_n = clean_text(name)
                    if len(clean_n) > 2:
                        pos = ExtractedPosition(
                            party_short_name=party_short,
                            organization_unit_type="NATIONAL",
                            official_title="National Convener",
                            hierarchy_level=1,
                            position_type="PARTY_ORGANIZATIONAL"
                        )
                        pol = ExtractedPolitician(
                            name=clean_n,
                            party_short_name=party_short,
                            stable_id=f"aap_{clean_n.lower().replace(' ', '_')}"
                        )
                        assignments.append(ExtractedAssignment(
                            position_ref=pos,
                            politician_ref=pol,
                            source_url=self.source_url,
                            source_type=self.source_type,
                            evidence_notes="Aam Aadmi Party National Convener"
                        ))

            # 2. Rajya Sabha Leader
            elif 'rajyasabhaleader' in th_text or 'rajya sabha leader' in th_text:
                names = [a.get_text(strip=True) for a in td.find_all('a') if not a.get_text(strip=True).startswith('[')]
                if not names:
                    names = [td.get_text(strip=True)]
                for name in names:
                    clean_n = clean_text(name)
                    if len(clean_n) > 2:
                        pos = ExtractedPosition(
                            party_short_name=party_short,
                            organization_unit_type="NATIONAL",
                            official_title="Leader of Parliamentary Party (Rajya Sabha)",
                            hierarchy_level=2,
                            position_type="PARLIAMENTARY_LEADER"
                        )
                        pol = ExtractedPolitician(
                            name=clean_n,
                            party_short_name=party_short,
                            stable_id=f"aap_{clean_n.lower().replace(' ', '_')}"
                        )
                        assignments.append(ExtractedAssignment(
                            position_ref=pos,
                            politician_ref=pol,
                            source_url=self.source_url,
                            source_type=self.source_type,
                            evidence_notes="AAP Parliamentary Leader in Rajya Sabha"
                        ))

        logger.info(f"AAPScraperAdapter extracted {len(assignments)} records")
        return assignments
