import os
import psycopg2
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")

def main():
    print("Connecting to Neon PostgreSQL...")
    try:
        conn = psycopg2.connect(DATABASE_URL)
        conn.autocommit = True
        cursor = conn.cursor()
        
        print("Enabling PostGIS and other extensions...")
        cursor.execute('CREATE EXTENSION IF NOT EXISTS postgis;')
        cursor.execute('CREATE EXTENSION IF NOT EXISTS postgis_topology;')
        cursor.execute('CREATE EXTENSION IF NOT EXISTS "uuid-ossp";')
        cursor.execute('CREATE EXTENSION IF NOT EXISTS pgcrypto;')
        
        print("Checking enabled extensions...")
        cursor.execute("SELECT extname FROM pg_extension;")
        extensions = [r[0] for r in cursor.fetchall()]
        print(f"Enabled extensions: {extensions}")
        
        cursor.close()
        conn.close()
        print("Database initialized successfully!")
    except Exception as e:
        print(f"Error initializing database: {e}")

if __name__ == "__main__":
    main()
