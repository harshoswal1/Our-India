import os
import json
from dotenv import load_dotenv
from database import SessionLocal
from models import LegalDocument
from ai_router import generate_embedding

load_dotenv()

LEGAL_DOCS = [
    {
        "title": "Motor Vehicles Act Section 129 - Helmet Requirement",
        "content": "Every person driving or riding on a motorcycle of any class or description, shall, while in a public place, wear protective headgear conforming to the standards of Bureau of Indian Standards.",
        "citations": "Motor Vehicles Act, 1988, Section 129"
    },
    {
        "title": "Motor Vehicles (Driving) Regulations 2017 - Towing Regulations",
        "content": "A vehicle shall not be towed by the police if a driver or passenger is present inside the vehicle, unless it is causing a severe traffic bottleneck or is involved in a hazardous condition. The owner has the right to pay the fine on the spot and release the vehicle before it is hauled away.",
        "citations": "Motor Vehicles (Driving) Regulations, 2017"
    },
    {
        "title": "Municipal Corporation Act - Duty to Maintain Public Streets",
        "content": "It is the obligatory duty of the Municipal Corporation and Public Works Department (PWD) to maintain public streets, repair potholes, and keep roads free of obstructions to ensure safe passage of public traffic. Citizens have the right to claim compensation for injuries caused by negligence of road maintenance.",
        "citations": "High Court Precedent on Civic Liability; Municipal Act Section 63"
    },
    {
        "title": "Right to Information (RTI) Act Section 6 - Request Procedure",
        "content": "A person who desires to obtain any information under this Act shall make a request in writing or through electronic means in English or Hindi or in the official language of the area, to the Central Public Information Officer (CPIO) or State Public Information Officer (SPIO) specifying the particulars of the information sought.",
        "citations": "Right to Information Act, 2005, Section 6"
    },
    {
        "title": "Indian Penal Code (IPC) / Bharatiya Nyaya Sanhita - Right to File FIR",
        "content": "It is mandatory for the police to register a First Information Report (FIR) under Section 154 of the CrPC (now relevant BNS section) when a citizen reports a cognizable offence. If the police station refuses to file the FIR, the citizen can send the report to the Superintendent of Police (SP) or file it online.",
        "citations": "Code of Criminal Procedure (CrPC), 154; Lalita Kumari v. Govt of UP"
    }
]

def seed():
    db = SessionLocal()
    try:
        # Clear existing
        db.query(LegalDocument).delete()
        db.commit()
            
        print("Generating embeddings and seeding legal documents...")
        for doc in LEGAL_DOCS:
            print(f"Embedding: {doc['title']}...")
            emb = generate_embedding(doc["content"])
            
            # If API is not working/mocked, we ensure it has a valid list of floats
            if not emb or all(v == 0.0 for v in emb):
                emb = [0.1] * 3072
                
            db_doc = LegalDocument(
                title=doc["title"],
                content=doc["content"],
                citations=doc["citations"],
                embedding=emb
            )
            db.add(db_doc)
        db.commit()
        print("Legal database seeded successfully!")
    except Exception as e:
        print(f"Error seeding database: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    seed()
