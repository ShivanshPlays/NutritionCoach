package com.nutritioncoach.tool;

import com.nutritioncoach.memory.InMemoryMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryTool.
 *
 * Strategy: verify the store → lookup lifecycle, multi-user isolation,
 * deduplication, and query filtering.  Uses InMemoryMemoryService as the
 * backing store — no Spring context, no database required.
 *
 * Phase 5 change: MemoryTool is now a facade over MemoryService (interface).
 * Tests create it with InMemoryMemoryService (the test-friendly implementation)
 * instead of the old ConcurrentHashMap.
 *
 * Why this approach instead of Mockito mocks?
 *   Using a real InMemoryMemoryService verifies the actual store → lookup
 *   round-trip, not just that delegate methods were called.  The behaviour
 *   (dedup, multi-user isolation, query filtering) is exercised end-to-end.
 *   MERN analogy: using Mastra's InMemoryStorage in Jest instead of jest.fn().
 *
 * MERN analogy: Jest tests for a Mastra Memory implementation, verifying
 * that saveMessages() and query() round-trip correctly with a mocked store.
 *
 * Book ref: Chapter 7 — Memory
 *   "Test memory tools like any other tool: store a known set of notes,
 *   retrieve with a query, assert correctness. The backing store
 *   (Map, SQLite, PostgreSQL) should be swappable without changing tests."
 */
class MemoryToolTest {

    /** Helper: create a MemoryTool backed by a fresh InMemoryMemoryService. */
    private static MemoryTool freshTool() {
        return new MemoryTool(new InMemoryMemoryService());
    }

    @Test
    void storeAndLookup_matchingQuery_returnsNote() {
        var tool = freshTool();

        tool.storeMemory("alice", "Coached on omega-3 fatty acids");

        List<String> results = tool.lookupNotes("alice", "omega-3");

        assertEquals(1, results.size(), "Should find 1 matching note");
        assertTrue(results.getFirst().contains("omega-3"));
    }

    @Test
    void lookupNotes_nonMatchingQuery_returnsEmptyList() {
        var tool = freshTool();

        tool.storeMemory("alice", "Coached on vitamin D");

        List<String> results = tool.lookupNotes("alice", "creatine");

        assertTrue(results.isEmpty(), "Non-matching query should return empty list");
    }

    @Test
    void lookupNotes_unknownUser_returnsEmptyList() {
        var tool = freshTool();
        // No notes stored for "bob"
        List<String> results = tool.lookupNotes("bob", "omega-3");

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Unknown user should return empty list");
    }

    @Test
    void lookupNotes_multipleNotes_allMatchingReturned() {
        var tool = freshTool();

        tool.storeMemory("alice", "Coached on omega-3 fatty acids");
        tool.storeMemory("alice", "Omega-3 follow-up: check EPA:DHA ratio");
        tool.storeMemory("alice", "Coached on magnesium");

        List<String> results = tool.lookupNotes("alice", "omega");

        assertEquals(2, results.size(), "Both omega-3 notes should be returned");
    }

    @Test
    void storeMemory_multiUser_isolatedByUserId() {
        var tool = freshTool();

        tool.storeMemory("alice", "Note for Alice about protein");
        tool.storeMemory("bob",   "Note for Bob about carbs");

        // Alice's lookup should not see Bob's note
        List<String> aliceResults = tool.lookupNotes("alice", "carbs");
        assertTrue(aliceResults.isEmpty(), "Alice's notes should not contain Bob's note");

        // Bob's lookup should not see Alice's note
        List<String> bobResults = tool.lookupNotes("bob", "protein");
        assertTrue(bobResults.isEmpty(), "Bob's notes should not contain Alice's note");
    }

    @Test
    void lookupNotes_blankQuery_returnsAllNotesForUser() {
        var tool = freshTool();

        tool.storeMemory("alice", "Note 1");
        tool.storeMemory("alice", "Note 2");

        List<String> results = tool.lookupNotes("alice", "");

        assertEquals(2, results.size(), "Blank query should return all notes");
    }

    @Test
    void getAllNotes_returnsAllNotesRegardlessOfQuery() {
        var tool = freshTool();

        tool.storeMemory("carol", "Topic A");
        tool.storeMemory("carol", "Topic B");

        List<String> all = tool.getAllNotes("carol");

        assertEquals(2, all.size());
    }

    @Test
    void storeMemory_duplicateNote_silentlyIgnored() {
        // Phase 5: dedup behaviour (InMemoryMemoryService mirrors JpaMemoryService dedup)
        var tool = freshTool();

        tool.storeMemory("alice", "Same note");
        tool.storeMemory("alice", "Same note"); // duplicate

        List<String> all = tool.getAllNotes("alice");
        assertEquals(1, all.size(), "Duplicate note must be silently ignored");
    }
}
