import json
import sys
from pathlib import Path

base_dir = Path(__file__).resolve().parent
data_dir = base_dir / "Data"

files = [
    data_dir / "INDIA_STATES.geojson",
    data_dir / "INDIA_DISTRICTS.geojson",
    data_dir / "INDIAN_SUB_DISTRICTS.geojson"
]

for f in files:
    try:
        with open(f, 'r', encoding='utf-8') as file:
            data = json.load(file)
            print(f"--- {f} ---")
            if 'features' in data and len(data['features']) > 0:
                print("Properties of first feature:")
                print(json.dumps(data['features'][0]['properties'], indent=2))
            else:
                print("No features found.")
    except Exception as e:
        print(f"Error reading {f}: {e}")
