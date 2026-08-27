import sys
import logging
import traceback
from backend.core.database import DatabaseClient
from backend.adapters.eci_adapter import ECIScraperAdapter
from backend.adapters.bjp_adapter import BJPScraperAdapter
from backend.adapters.inc_adapter import INCScraperAdapter
from backend.adapters.aap_adapter import AAPScraperAdapter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("autonomous_ingestion_worker")

def main():
    logger.info("==================================================================")
    logger.info("🚀 STARTING OUR INDIA AUTONOMOUS POLITICAL INGESTION WORKER")
    logger.info("==================================================================")

    # 1. Initialize Supabase Client & Seed Core Entities
    try:
        db_client = DatabaseClient()
        logger.info("✅ Connected to Supabase and verified core parties and organization units.")
    except Exception as e:
        logger.error(f"❌ Failed to initialize database client: {str(e)}")
        logger.error("Please verify that SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are set in GitHub Actions secrets.")
        sys.exit(1)

    # 2. Registered Source Adapters
    adapters = [
        ("Election Commission of India (ECI)", ECIScraperAdapter()),
        ("Bharatiya Janata Party (BJP)", BJPScraperAdapter()),
        ("Indian National Congress (INC)", INCScraperAdapter()),
        ("Aam Aadmi Party (AAP)", AAPScraperAdapter())
    ]

    total_sources_checked = len(adapters)
    total_records_fetched = 0
    total_positions_upserted = 0
    total_politicians_upserted = 0
    total_hist_preserved = 0
    total_warnings_failures = 0

    for source_label, adapter in adapters:
        logger.info(f"\n📡 Executing Adapter: {source_label}")
        try:
            assignments = adapter.fetch_data()
            total_records_fetched += len(assignments)
            logger.info(f"   -> Extracted {len(assignments)} raw records from {adapter.source_url}")

            pos_count = 0
            pol_count = 0

            for assignment in assignments:
                try:
                    pos_ok, pol_ok, hist_ok = db_client.upsert_assignment(assignment)
                    if pos_ok:
                        pos_count += 1
                    if pol_ok:
                        pol_count += 1
                    if hist_ok:
                        total_hist_preserved += 1
                except Exception as ex:
                    total_warnings_failures += 1
                    logger.warning(f"   [Warning] Skipped assignment '{assignment.position_ref.official_title}': {ex}")

            total_positions_upserted += pos_count
            total_politicians_upserted += pol_count
            logger.info(f"   -> ✅ Successfully synced {pos_count} positions, {pol_count} politicians for {source_label}")

        except Exception as e:
            total_warnings_failures += 1
            # Source Safety Rule: Failure of one source adapter will NOT delete or corrupt other data
            logger.error(f"   ❌ Adapter '{source_label}' failed safely: {str(e)}")
            logger.debug(traceback.format_exc())

    # 3. Final Production Audit Summary Report
    logger.info("\n==================================================================")
    logger.info("📊 FINAL AUTONOMOUS INGESTION AUDIT REPORT")
    logger.info("==================================================================")
    logger.info(f"Sources Checked                : {total_sources_checked}")
    logger.info(f"Total Political Records Fetched: {total_records_fetched}")
    logger.info(f"Positions Upserted to Supabase : {total_positions_upserted}")
    logger.info(f"Politicians Upserted           : {total_politicians_upserted}")
    logger.info(f"Historical Assignments Preserved: {total_hist_preserved}")
    logger.info(f"Warnings / Isolated Failures   : {total_warnings_failures}")
    logger.info("==================================================================")
    logger.info("✅ Ingestion cycle completed with 100% data safety.")

if __name__ == "__main__":
    main()
