package com.nutritioncoach.memory;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * NoteType — closed enumeration of agent note classifiers
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Replaces the free-text `noteType` String to prevent magic-string bugs
 * and make the full vocabulary visible at compile time.
 *
 * Stored as VARCHAR in the DB via @Enumerated(EnumType.STRING) — the
 * enum name (e.g. "COACHING") is persisted, so renaming an enum constant
 * requires a DB migration.
 *
 * MERN/Next.js analogy:
 *   // TypeScript equivalent — a union type or const enum:
 *   type NoteType = 'COACHING' | 'RESEARCH' | 'SUMMARY' | 'FACT' | 'PREFERENCE'
 *   // or: const NoteType = { COACHING: 'COACHING', ... } as const
 *   Using a TS union eliminates the same magic-string risk.
 *
 * Book ref: Chapter 7 — Memory
 *   "Use structured types for memory classifiers — unstructured strings
 *    make it impossible to query or filter memory by type reliably."
 * ══════════════════════════════════════════════════════════════════════════
 */
public enum NoteType {

    /**
     * Notes written by the CoachAgent or MemoryTool during a coaching session.
     * Example: "User is training for a marathon and needs 3 200-cal snacks/day."
     */
    COACHING,

    /**
     * Notes written by the ResearchAgent after retrieving nutritional research.
     * Example: "Creatine monohydrate: 3-5 g/day is evidence-based for strength."
     */
    RESEARCH,

    /**
     * Compressed session summaries written by LoggerAgent after each turn.
     * Keeps a compact, searchable record of what was discussed.
     */
    SUMMARY,

    /**
     * Individual key facts extracted by LoggerAgent from the conversation.
     * Example: "User is lactose intolerant."
     */
    FACT,

    /**
     * Explicit dietary preferences not yet promoted to UserProfile fields.
     * Example: "Prefers Mediterranean-style meals."
     */
    PREFERENCE
}
