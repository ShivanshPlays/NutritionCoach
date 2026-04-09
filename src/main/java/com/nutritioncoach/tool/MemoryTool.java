package com.nutritioncoach.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
 *   In Spring, the equivalent is a @Component service that wraps a
 *   persistence layer.  Phase 3 uses an in-memory ConcurrentHashMap
 *   (like a JS Map) for zero-config setup.  Phase 4 replaces this with
 *   a JPA entity (agent_note table) backed by H2/PostgreSQL.
 *
 * Implementation notes:
 *   The ConcurrentHashMap is thread-safe for concurrent HTTP requests.
 *   It is reset on every application restart — this is intentional for
 *   Phase 3 (stateless dev iteration).  Phase 4 introduces Flyway
 *   migrations and persistent JPA storage.
 *
 *   lookupNotes() performs a case-insensitive substring match.
 *   Phase 10 (RAG) will upgrade this to embedding-based similarity search
 *   against a pgvector store for semantic retrieval.
 *
 * Book ref: Chapter 7 — Memory
 *   "Short-term memory = the conversation window.
 *    Long-term memory  = persistent notes and user profile."
 *   This tool represents the long-term note storage layer.
 *   The short-term (conversation window) is added in Phase 4.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class MemoryTool {

    // Phase 3: in-memory store.  Phase 4: replaced by JPA AgentNote entity.
    // MERN analogy: a plain JS Map<string, string[]> — replaced by a DB query.
    private final ConcurrentHashMap<String, List<String>> notes = new ConcurrentHashMap<>();

    /**
     * Persist a free-text note associated with a userId.
     *
     * @param userId the user who owns this note (Phase 3: any string key)
     * @param note   the text to store
     *
     * MERN analogy: memory.save({ userId, content }) or a DB INSERT in a
     * Next.js API route.
     */
    public void storeMemory(String userId, String note) {
        notes.computeIfAbsent(userId, k -> new ArrayList<>()).add(note);
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
        List<String> all = notes.getOrDefault(userId, Collections.emptyList());
        if (query == null || query.isBlank()) {
            return new ArrayList<>(all);
        }
        String lq = query.toLowerCase();
        return all.stream()
                .filter(n -> n.toLowerCase().contains(lq))
                .toList();
    }

    /**
     * Return all notes for a userId regardless of query.
     * Used for prompt context injection (include everything the agent has noted).
     *
     * @param userId the user whose notes to retrieve
     * @return all stored notes for this user
     */
    public List<String> getAllNotes(String userId) {
        return new ArrayList<>(notes.getOrDefault(userId, Collections.emptyList()));
    }
}
