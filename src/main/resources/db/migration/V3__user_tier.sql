-- ═══════════════════════════════════════════════════════════════════════
-- V3__user_tier.sql — add tier column to user_profile
-- ═══════════════════════════════════════════════════════════════════════
--
-- Phase 7: Dynamic Agents — introduces UserTier (FREE / PREMIUM).
-- The tier is stored here so Phase 8 (LoggerAgent) can upgrade it
-- automatically based on usage patterns.
--
-- MERN analogy: like adding a `plan: 'free' | 'premium'` field to your
-- Prisma User model in a new migration file.
--
-- DEFAULT 'FREE' ensures all existing rows get the FREE tier without
-- requiring a data backfill — safe for H2 and future PostgreSQL.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE user_profile
    ADD COLUMN tier VARCHAR(16) NOT NULL DEFAULT 'FREE';
