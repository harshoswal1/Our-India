import os
import subprocess
import gzip
import shutil

data_dir = "d:\\Our-India\\Data"
out_dir = "d:\\Our-India\\android\\app\\src\\main\\assets\\geo"

os.makedirs(out_dir, exist_ok=True)

files_to_process = [
    {
        "in": "INDIA_STATES.geojson",
        "out": "states.geojson",
        "simplify": "2%",
        "filter": "STNAME_SH",
        "rename": "state=STNAME_SH"
    },
    {
        "in": "INDIA_DISTRICTS.geojson",
        "out": "districts.geojson",
        "simplify": "1.5%",
        "filter": "stname,dtname",
        "rename": "state=stname,district=dtname"
    },
    {
        "in": "INDIAN_SUB_DISTRICTS.geojson",
        "out": "sub_districts.geojson",
        "simplify": "1.5%",
        "filter": "stname,dtname,sdtname",
        "rename": "state=stname,district=dtname,subdistrict=sdtname"
    }
]

for item in files_to_process:
    in_path = os.path.join(data_dir, item["in"])
    out_path = os.path.join(out_dir, item["out"])
    
    # Run mapshaper
    cmd = [
        "npx", "-y", "mapshaper", in_path,
        "-simplify", f"resolution=1000", # or percentage
    ]
    # Actually percentage is better
    cmd = f'npx -y mapshaper "{in_path}" -simplify {item["simplify"]} keep-shapes -filter-fields {item["filter"]} -rename-fields {item["rename"]} -o format=geojson "{out_path}"'
    
    print(f"Running: {cmd}")
    subprocess.run(cmd, shell=True, check=True)
    
    # Gzip
    out_gz = out_path + ".gz"
    with open(out_path, 'rb') as f_in:
        with gzip.open(out_gz, 'wb', compresslevel=9) as f_out:
            shutil.copyfileobj(f_in, f_out)
            
    # Print sizes
    orig_size = os.path.getsize(in_path) / (1024 * 1024)
    simp_size = os.path.getsize(out_path) / (1024 * 1024)
    gz_size = os.path.getsize(out_gz) / (1024 * 1024)
    
    print(f"{item['in']}:")
    print(f"  Original: {orig_size:.2f} MB")
    print(f"  Simplified: {simp_size:.2f} MB")
    print(f"  Gzipped: {gz_size:.2f} MB\n")
    
    # Remove the unzipped out_path to save space, we will read the gz from Android
    os.remove(out_path)
