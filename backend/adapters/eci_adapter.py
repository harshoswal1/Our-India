import logging
import requests
from bs4 import BeautifulSoup
from typing import List
from backend.core.models import ExtractedAssignment, ExtractedPolitician, ExtractedPosition

logger = logging.getLogger(__name__)

class ECIScraperAdapter:
    """
    Real adapter for scraping the Election Commission of India from Wikipedia.
    """
    def __init__(self):
        self.source_url = "https://en.wikipedia.org/wiki/Election_Commission_of_India"
        self.source_type = "MEDIA"

    def fetch_data(self) -> List[ExtractedAssignment]:
        logger.info(f"Connecting to ECI source at {self.source_url}")
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        }
        
        response = requests.get(self.source_url, headers=headers, timeout=15)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, 'html.parser')
        
        assignments = []
        neutral_party_id = "00000000-0000-0000-0000-000000000000"
        
        # Look for the exact rows in the infobox
        for th in soup.find_all('th', class_='infobox-label'):
            header = th.get_text(separator=" ", strip=True).lower()
            if 'commissioner' in header:
                td = th.find_next_sibling('td')
                if td:
                    title = th.get_text(separator=" ", strip=True)
                    names = [li.get_text(strip=True).split('[')[0] for li in td.find_all('li')]
                    if not names:
                        names = [td.get_text(strip=True).split('[')[0]]
                        
                    for name in names:
                        position = ExtractedPosition(
                            party_id=neutral_party_id,
                            organization_unit_type="NATIONAL",
                            official_title=title,
                            hierarchy_level=1,
                            position_type="GOVERNMENT_APPOINTMENT"
                        )
                        politician = ExtractedPolitician(
                            name=name,
                            party_id=neutral_party_id
                        )
                        assignment = ExtractedAssignment(
                            position_ref=position,
                            politician_ref=politician,
                            source_url=self.source_url,
                            source_type=self.source_type
                        )
                        assignments.append(assignment)
                            
        return assignments
