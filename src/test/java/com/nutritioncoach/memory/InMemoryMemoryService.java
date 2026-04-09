package com.nutritioncoach.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * InMemoryMemoryService — Test implementation of MemoryService
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Used only in unit tests (src/test/java).  No Spring context, no JPA,
 * no database required.  Backed by plain Java collections.
 *
 * Why this class exists:
 *   JpaMemoryService requires JPA repositories (Spring context + H2/PostgreSQL).
 *   Unit tests for MemoryTool and CoachAgent are fast, Spring-free tests.
 *   Providing an in-memory implementation lets those tests use real
 *   MemoryService behaviour without database plumbing.
 *
 * MERN/Next.js analogy:
 *   This is Mastra's InMemoryStorage:
 *     const memory = new Memory({ storage: new InMemoryStorage() })
 *   In Jest tests you'd use this stub instead of LibSQLStore so tests
 *   run fast and don't require a real database connection.
 *
 *   In Spring tests, an equivalent approach would be using @DataJpaTest to
 *   spin up an H2 context — but that's slower and overkill for unit tests
 *   that don't care about the SQL layer.
 *
 * Thread safety: uses ConcurrentHashMap to match JpaMemoryService's
 * transaction isolation (concurrent writes to the same key are safe).
 *
 * Dedup: save-note dedup is same as production — content equality check.
 *
 * Phase 10 note: once vector search is added to the interface, this class
 * will fall back to substring matching (same as now).
 *
 * Book ref: Chapter 7 — Memory
 *   "The backing store is an implementation detail. Test with a fast stub,
 *    deploy with a durable store."
 * ══════════════════════════════════════════════════════════════════════════
 */
public class InMemoryMemoryService implements MemoryService {

    // userId → ordered list of ConversationMessage (insertion order = time order)
    // MERN analogy: Map<string, Message[]> in a JS in-memory store
    private final Map<String, List<ConversationMessage>> messages = new ConcurrentHashMap<>();

    // userId → ordered list of note content strings
    // MERN analogy: Map<string, string[]> for agent notes
    private final Map<String, List<String>> notes = new ConcurrentHashMap<>();

    @Override
    public void saveMessage(String userId, String role, String content) {
        messages.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new ConversationMessage(userId, role, content));
    }

    @Override
    public List<ConversationMessage> getRecentMessages(String userId, int n) {
        List<ConversationMessage> all = messages.getOrDefault(userId, List.of());
        // Take the last n in insertion order (= chronological), already oldest-first.
        List<ConversationMessage> result = all.subList(Math.max(0, all.size() - n), all.size());
        return new ArrayList<>(result);
    }

    @Override
    public void saveNote(String userId, String noteType, String content) {
        if (content == null || content.isBlank()) return;
        List<String> userNotes = notes.computeIfAbsent(userId, k -> new ArrayList<>());
        // Dedup: same as JpaMemoryService — silently skip exact duplicate content.
        if (!userNotes.contains(content)) {
            userNotes.add(content);
        }
    }

    @Override
    public List<String> findNotes(String userId, String query) {
        List<String> all = notes.getOrDefault(userId, List.of());
        if (query == null || query.isBlank()) {
            // Return all in reverse insertion order (newest-first) to match JpaMemoryService.
            List<String> copy = new ArrayList<>(all);
            Collections.reverse(copy);
            return copy;
        }
        String lq = query.toLowerCase();
        // Reverse so newest comes first (matches JPA ORDER BY created_at DESC).
        List<String> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        return reversed.stream()
                .filter(n -> n.toLowerCase().contains(lq))
                .toList();
    }
}
