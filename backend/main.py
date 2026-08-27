import logging
import sys
import traceback
from backend.core.database import DatabaseClient
from backend.adapters.eci_adapter import ECIScraperAdapter

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger("ingestion_worker")

def main():
    logger.info("Starting Our India Autonomous Ingestion Worker...")
    
    try:
        # Initialize secure Supabase client
        db_client = DatabaseClient()
        logger.info("Successfully connected to Supabase.")
    except Exception as e:
        logger.error(f"Failed to initialize database client: {str(e)}")
        sys.exit(1)

    # Initialize Adapters
    # In the future, this list will dynamically load all party adapters.
    adapters = [
        ECIScraperAdapter()
    ]
    
    for adapter in adapters:
        try:
            logger.info(f"Running adapter: {adapter.__class__.__name__}")
            assignments = adapter.fetch_data()
            
            # Pipeline: Upsert to Supabase safely
            for assignment in assignments:
                try:
                    db_client.upsert_assignment(assignment)
                except Exception as ex:
                    logger.error(f"Failed to upsert assignment for {assignment.position_ref.official_title}: {str(ex)}")
                    
            logger.info(f"Adapter {adapter.__class__.__name__} completed successfully.")
            
        except Exception as e:
            # Source Safety Rule: If an adapter fails, we catch the exception and log it.
            # We DO NOT delete existing data. The pipeline moves on to the next adapter.
            logger.error(f"Source adapter {adapter.__class__.__name__} failed: {str(e)}")
            logger.error(traceback.format_exc())

    logger.info("Ingestion Worker completed all tasks safely.")

if __name__ == "__main__":
    main()
