-- ═══════════════════════════════════════════════════════════════════════════
-- V2__agent_note_dedup.sql — Phase 4: deduplication constraint on agent_note
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Why this is a separate migration (not in V1):
--   Separating concerns across migration versions makes partial rollback
--   and future alteration easier.  If we ever need a compound dedup key
--   (e.g. user_id + note_type + content) we add V3 without touching V2.
--
-- Dedup strategy:
--   DB-level: UNIQUE constraint is the authoritative guard — rejects
--   duplicates even from concurrent threads that race past the app-level check.
--   App-level: MemoryService.saveNote() checks existence before inserting
--   (or catches DataIntegrityViolationException) so the caller never sees
--   an exception for duplicate notes.
--
-- MERN/Next.js analogy:
--   Like a Prisma @@unique([userId, content]) constraint on the Note model,
--   or a MongoDB unique compound index: { userId: 1, content: 1 }.
--
-- Book ref: Chapter 7 — Memory
--   Idempotent writes to memory are essential when agents may retry actions
--   or when the same coaching topic is requested multiple times.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE agent_note
    ADD CONSTRAINT uq_agent_note_user_content UNIQUE (user_id, content);
