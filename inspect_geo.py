import json
import sys

files = [
    "d:\\Our-India\\Data\\INDIA_STATES.geojson",
    "d:\\Our-India\\Data\\INDIA_DISTRICTS.geojson",
    "d:\\Our-India\\Data\\INDIAN_SUB_DISTRICTS.geojson"
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
