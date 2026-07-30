import models
from database import engine, Base

def main():
    print("Creating all tables in Neon database via SQLAlchemy...")
    try:
        Base.metadata.create_all(bind=engine)
        print("Tables created successfully!")
    except Exception as e:
        print(f"Error creating tables: {e}")

if __name__ == "__main__":
    main()
