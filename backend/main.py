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
    logger.info("🚀 OUR INDIA AUTONOMOUS POLITICAL INGESTION WORKER")
    logger.info("==================================================================")

    # 1. Initialize Supabase Client & Seed Core Entities
    try:
        db_client = DatabaseClient()
        logger.info("✅ Connected to Supabase and verified core parties & organization units.")
    except Exception as e:
        logger.error(f"❌ Failed to initialize database client: {str(e)}")
        logger.error(traceback.format_exc())
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
        logger.info(f"\n------------------------------------------------------------------")
        logger.info(f"📡 Executing Source Adapter: {source_label}")
        logger.info(f"   Source URL: {adapter.source_url}")
        
        try:
            assignments = adapter.fetch_data()
            count = len(assignments)
            total_records_fetched += count
            
            if count == 0:
                logger.error(f"❌ Adapter '{source_label}' extracted 0 records!")
                total_warnings_failures += 1
                continue

            logger.info(f"   [Extraction] Raw records extracted   : {count}")
            logger.info(f"   [Validation] Pydantic validated count : {count}")

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
                    logger.error(f"   ❌ Failed assignment for '{assignment.position_ref.official_title}': {ex}")
                    logger.error(traceback.format_exc())

            total_positions_upserted += pos_count
            total_politicians_upserted += pol_count
            logger.info(f"   [Database] Positions upserted        : {pos_count}")
            logger.info(f"   [Database] Politicians upserted      : {pol_count}")
            logger.info(f"   ✅ Successfully synced {pos_count} positions for {source_label}")

        except Exception as e:
            total_warnings_failures += 1
            logger.error(f"   ❌ Adapter '{source_label}' execution failed: {str(e)}")
            logger.error(traceback.format_exc())

    # 3. Final Production Database Verification
    logger.info("\n==================================================================")
    logger.info("🔍 EXECUTING FINAL DATABASE VERIFICATION IN SUPABASE")
    logger.info("==================================================================")
    
    try:
        final_counts = db_client.verify_final_database_counts()
        for tbl, cnt in final_counts.items():
            logger.info(f"   public.{tbl:<32}: {cnt} records")
            
        parties_cnt = final_counts.get("parties", 0)
        org_cnt = final_counts.get("political_organization_units", 0)
        pos_cnt = final_counts.get("political_positions", 0)
        pol_cnt = final_counts.get("politicians", 0)
        assign_cnt = final_counts.get("political_position_assignments", 0)
        
        has_error = False
        if parties_cnt <= 0:
            logger.error("❌ CRITICAL: 'parties' table has 0 records!")
            has_error = True
        if org_cnt <= 0:
            logger.error("❌ CRITICAL: 'political_organization_units' table has 0 records!")
            has_error = True
        if pos_cnt <= 0:
            logger.error("❌ CRITICAL: 'political_positions' table has 0 records!")
            has_error = True
        if pol_cnt <= 0:
            logger.error("❌ CRITICAL: 'politicians' table has 0 records!")
            has_error = True
        if assign_cnt <= 0:
            logger.error("❌ CRITICAL: 'political_position_assignments' table has 0 records!")
            has_error = True
            
        if has_error:
            logger.error("\n❌ CRITICAL ERROR: Ingestion failed to populate remote database!")
            sys.exit(1)
            
        logger.info("\n🎉 SUCCESS: Remote Supabase database verified with live production records!")
        
    except Exception as e:
        logger.error(f"❌ Failed to verify database counts: {e}")
        logger.error(traceback.format_exc())
        sys.exit(1)

    # 4. Final Summary Report
    logger.info("\n==================================================================")
    logger.info("📊 FINAL INGESTION AUDIT SUMMARY")
    logger.info("==================================================================")
    logger.info(f"Sources Checked                 : {total_sources_checked}")
    logger.info(f"Total Political Records Fetched : {total_records_fetched}")
    logger.info(f"Positions Upserted to Supabase  : {total_positions_upserted}")
    logger.info(f"Politicians Upserted            : {total_politicians_upserted}")
    logger.info(f"Historical Assignments Preserved: {total_hist_preserved}")
    logger.info(f"Warnings / Failures             : {total_warnings_failures}")
    logger.info("==================================================================")
    logger.info("✅ Ingestion cycle finished.")

if __name__ == "__main__":
    main()
