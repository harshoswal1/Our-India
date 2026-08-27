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

class BJPScraperAdapter:
    """Real source adapter for Bharatiya Janata Party (BJP) national office-bearers."""
    def __init__(self):
        self.source_url = "https://en.wikipedia.org/wiki/Bharatiya_Janata_Party"
        self.source_type = "MEDIA"

    def fetch_data(self) -> List[ExtractedAssignment]:
        logger.info(f"Fetching BJP leadership from {self.source_url}")
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        
        response = requests.get(self.source_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        
        assignments = []
        party_short = "BJP"

        for tr in soup.find_all('tr'):
            th = tr.find('th')
            td = tr.find('td')
            if not th or not td:
                continue

            th_text = th.get_text(strip=True).lower()

            # 1. National President
            if th_text == 'president':
                president_name = clean_text(td.get_text(strip=True))
                if president_name:
                    pos = ExtractedPosition(
                        party_short_name=party_short,
                        organization_unit_type="NATIONAL",
                        official_title="National President",
                        hierarchy_level=1,
                        position_type="PARTY_ORGANIZATIONAL"
                    )
                    pol = ExtractedPolitician(
                        name=president_name,
                        party_short_name=party_short,
                        stable_id=f"bjp_{president_name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="BJP National President"
                    ))

            # 2. Lok Sabha Leader
            elif 'loksabhaleader' in th_text or 'lok sabha leader' in th_text:
                leader_name = clean_text(td.get_text(strip=True))
                if leader_name:
                    pos = ExtractedPosition(
                        party_short_name=party_short,
                        organization_unit_type="NATIONAL",
                        official_title="Leader of Lok Sabha",
                        hierarchy_level=2,
                        position_type="PARLIAMENTARY_LEADER"
                    )
                    pol = ExtractedPolitician(
                        name=leader_name,
                        party_short_name=party_short,
                        stable_id=f"bjp_{leader_name.lower().replace(' ', '_')}"
                    )
                    assignments.append(ExtractedAssignment(
                        position_ref=pos,
                        politician_ref=pol,
                        source_url=self.source_url,
                        source_type=self.source_type,
                        evidence_notes="BJP Parliamentary Party Leader in Lok Sabha"
                    ))

            # 3. National General Secretaries
            elif 'general secretary' in th_text:
                gs_names = [a.get_text(strip=True) for a in td.find_all('a') if a.get_text(strip=True) and not a.get_text(strip=True).startswith('[') and a.get_text(strip=True) != 'List']
                for name in gs_names:
                    clean_n = clean_text(name)
                    if len(clean_n) > 2:
                        pos = ExtractedPosition(
                            party_short_name=party_short,
                            organization_unit_type="NATIONAL",
                            official_title=f"National General Secretary ({clean_n})",
                            hierarchy_level=3,
                            position_type="PARTY_ORGANIZATIONAL"
                        )
                        pol = ExtractedPolitician(
                            name=clean_n,
                            party_short_name=party_short,
                            stable_id=f"bjp_{clean_n.lower().replace(' ', '_')}"
                        )
                        assignments.append(ExtractedAssignment(
                            position_ref=pos,
                            politician_ref=pol,
                            source_url=self.source_url,
                            source_type=self.source_type,
                            evidence_notes="BJP National General Secretary"
                        ))

            # 4. National Vice Presidents
            elif 'current vice' in th_text or 'vice president' in th_text:
                vp_names = [a.get_text(strip=True) for a in td.find_all('a') if a.get_text(strip=True) and not a.get_text(strip=True).startswith('[')]
                for name in vp_names:
                    clean_n = clean_text(name)
                    if len(clean_n) > 2:
                        pos = ExtractedPosition(
                            party_short_name=party_short,
                            organization_unit_type="NATIONAL",
                            official_title=f"National Vice President ({clean_n})",
                            hierarchy_level=2,
                            position_type="PARTY_ORGANIZATIONAL"
                        )
                        pol = ExtractedPolitician(
                            name=clean_n,
                            party_short_name=party_short,
                            stable_id=f"bjp_{clean_n.lower().replace(' ', '_')}"
                        )
                        assignments.append(ExtractedAssignment(
                            position_ref=pos,
                            politician_ref=pol,
                            source_url=self.source_url,
                            source_type=self.source_type,
                            evidence_notes="BJP National Vice President"
                        ))

        logger.info(f"BJPScraperAdapter extracted {len(assignments)} records")
        return assignments
