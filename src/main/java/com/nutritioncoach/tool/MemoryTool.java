package com.nutritioncoach.tool;

import com.nutritioncoach.memory.MemoryService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * MemoryTool — Store and retrieve short notes per user
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Tool profile (Phase 3 documentation requirement):
 *   storeMemory:
 *     Input:   userId (String), note (String)
 *     Output:  void
 *     Read-only or mutating? MUTATING — writes a note for the user
 *     Safe for the model to call without human confirmation? YES —
 *       only stores text notes, no side-effects outside this service
 *
 *   lookupNotes:
 *     Input:   userId (String), query (String)
 *     Output:  List<String> — matching notes
 *     Read-only or mutating? READ-ONLY
 *     Safe for the model to call without human confirmation? YES
 *
 * MERN/Next.js analogy:
 *   In Mastra, memory is provided by the built-in Memory class backed by
 *   LibSQLStore:
 *     const memory = new Memory({ storage: new LibSQLStore({ url }) })
 *     await memory.saveMessages({ messages, config: { ... } })
 *     await memory.query({ threadId, query })
 *
 *   In Spring, this @Component is a thin facade over MemoryService (JPA).
 *   The facade pattern keeps CoachAgent isolated from persistence details —
 *   Phase 10 can swap the backing store without touching the agent.
 *
 * Phase 4 change:
 *   Replaced in-memory ConcurrentHashMap with MemoryService (JPA / H2).
 *   Deduplication: MemoryService.saveNote() is idempotent — duplicate notes
 *   are silently ignored (app-level check + DB unique constraint as guard).
 *   Phase 4 dedup strategy: UNIQUE constraint on (user_id, content) +
 *   INSERT ON CONFLICT DO NOTHING (idempotent upsert via saveNote).
 *
 * Book ref: Chapter 7 — Memory
 *   "Short-term memory = the conversation window.
 *    Long-term memory  = persistent notes and user profile."
 *   This tool represents the long-term note storage layer.
 *   The short-term (conversation window) is managed by MemoryService directly
 *   and injected into ChatController prompts.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component

// How does Spring know which MemoryService to inject?
// - In production, there is only one @Service implementing MemoryService: JpaMemoryService.
//   Spring finds this bean and injects it into MemoryTool's constructor.
// - In tests, you manually construct MemoryTool with your own MemoryService (e.g., InMemoryMemoryService).
// - If there are multiple beans implementing MemoryService in the context, Spring will throw an error:
//   "NoUniqueBeanDefinitionException: expected single matching bean but found 2".
//   To resolve this, you can use @Primary on the default bean, or @Qualifier to specify which one to inject.
//
// Book ref: Chapter 7 — Memory (backing-store abstraction)
// MERN analogy: If you have two different storage backends, you must tell your DI system which one to use.
//
@Component
public class MemoryTool {

    // Phase 4: delegates to MemoryService (JPA-backed).
    // MERN analogy: injecting a db client or Prisma instance via constructor DI.
    private final MemoryService memoryService;

    public MemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Persist a free-text note associated with a userId.
     * Idempotent — calling this twice with the same arguments is safe.
     *
     * @param userId the user who owns this note
     * @param note   the text to store
     *
     * MERN analogy: memory.save({ userId, content }) or a DB INSERT/upsert in a
     * Next.js API route.
     */
    public void storeMemory(String userId, String note) {
        memoryService.saveNote(userId, "coaching", note);
    }

    /**
     * Retrieve notes for a userId that contain the query string.
     *
     * @param userId the user whose notes to search
     * @param query  case-insensitive substring to match
     * @return list of matching note strings (empty list if none)
     *
     * MERN analogy: memory.query({ userId, query }) or a
     * SELECT * FROM agent_notes WHERE content ILIKE '%query%' in Prisma.
     */
    public List<String> lookupNotes(String userId, String query) {
        return memoryService.findNotes(userId, query);
    }

    /**
     * Return all notes for a userId regardless of query.
     * Used for prompt context injection (include everything the agent has noted).
     *
     * @param userId the user whose notes to retrieve
     * @return all stored notes for this user
     */
    public List<String> getAllNotes(String userId) {
        return memoryService.findNotes(userId, null);
    }
}
