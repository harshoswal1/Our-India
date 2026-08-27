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

class INCScraperAdapter:
    """Real source adapter for Indian National Congress (INC) national office-bearers."""
    def __init__(self):
        self.source_url = "https://en.wikipedia.org/wiki/Indian_National_Congress"
        self.source_type = "MEDIA"

    def fetch_data(self) -> List[ExtractedAssignment]:
        logger.info(f"Fetching INC leadership from {self.source_url}")
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        
        response = requests.get(self.source_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        
        assignments = []
        party_short = "INC"

        for tr in soup.find_all('tr'):
            th = tr.find('th')
            td = tr.find('td')
            if not th or not td:
                continue

            th_text = th.get_text(strip=True).lower()

            # 1. Congress President
            if th_text == 'president':
                pres_name = clean_text(td.get_text(strip=True))
                if pres_name:
                    pos = ExtractedPosition(
                        party_short_name=party_short,
                        organization_unit_type="NATIONAL",
                        official_title="Congress President",
                        hierarchy_level=1,
                        position_type="PARTY_ORGANIZATIONAL"
                    )
                    pol = ExtractedPolitician(
                        name=pres_name,
                        party_short_name=party_short,
                        stable_id=f"inc_{pres_name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="Indian National Congress President (AICC)"
                    ))

            # 2. Parliamentary Party Chairperson
            elif 'parliamentary' in th_text:
                chair_name = clean_text(td.get_text(strip=True))
                if chair_name:
                    pos = ExtractedPosition(
                        party_short_name=party_short,
                        organization_unit_type="NATIONAL",
                        official_title="Parliamentary Party Chairperson",
                        hierarchy_level=1,
                        position_type="PARLIAMENTARY_LEADER"
                    )
                    pol = ExtractedPolitician(
                        name=chair_name,
                        party_short_name=party_short,
                        stable_id=f"inc_{chair_name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="Congress Parliamentary Party Chairperson"
                    ))

            # 3. Lok Sabha Leader
            elif 'loksabhaleader' in th_text or 'lok sabha leader' in th_text:
                leader_name = clean_text(td.get_text(strip=True))
                if leader_name:
                    pos = ExtractedPosition(
                        party_short_name=party_short,
                        organization_unit_type="NATIONAL",
                        official_title="Leader of the Opposition (Lok Sabha)",
                        hierarchy_level=2,
                        position_type="PARLIAMENTARY_LEADER"
                    )
                    pol = ExtractedPolitician(
                        name=leader_name,
                        party_short_name=party_short,
                        stable_id=f"inc_{leader_name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="Leader of Opposition in Lok Sabha"
                    ))

            # 4. AICC General Secretaries
            elif 'general secretary' in th_text:
                gs_names = [a.get_text(strip=True) for a in td.find_all('a') if a.get_text(strip=True) and not a.get_text(strip=True).startswith('[') and a.get_text(strip=True) != 'List']
                for name in gs_names:
                    clean_n = clean_text(name)
                    if len(clean_n) > 2:
                        pos = ExtractedPosition(
                            party_short_name=party_short,
                            organization_unit_type="NATIONAL",
                            official_title=f"AICC General Secretary ({clean_n})",
                            hierarchy_level=3,
                            position_type="PARTY_ORGANIZATIONAL"
                        )
                        pol = ExtractedPolitician(
                            name=clean_n,
                            party_short_name=party_short,
                            stable_id=f"inc_{clean_n.lower().replace(' ', '_')}"
                        )
                        assignments.append(ExtractedAssignment(
                            position_ref=pos,
                            politician_ref=pol,
                            source_url=self.source_url,
                            source_type=self.source_type,
                            evidence_notes="All India Congress Committee General Secretary"
                        ))

        logger.info(f"INCScraperAdapter extracted {len(assignments)} records")
        return assignments
