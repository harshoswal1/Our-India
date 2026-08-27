-- Migration 01: Safe Constraints Setup
-- Idempotent script to apply necessary deduplication constraints

DO $$
BEGIN
    -- 1. Ensure political_positions has the unique constraint for matching
    -- Scope: party + org unit + official title
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_position_identity'
    ) THEN
        ALTER TABLE political_positions 
        ADD CONSTRAINT uq_position_identity UNIQUE (party_id, organization_unit_type, official_title);
    END IF;
    
    -- 2. Ensure political_position_assignments guarantees only one active person per position
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'uq_active_assignment'
    ) THEN
        CREATE UNIQUE INDEX uq_active_assignment 
        ON political_position_assignments (position_id) WHERE is_active = true;
    END IF;
END $$;
