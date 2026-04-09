package com.nutritioncoach.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryTool.
 *
 * Strategy: verify the store → lookup lifecycle, multi-user isolation,
 * and query filtering.  All state is in-memory so tests are isolated
 * as long as each test creates a fresh MemoryTool instance.
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

    @Test
    void storeAndLookup_matchingQuery_returnsNote() {
        var tool = new MemoryTool();

        tool.storeMemory("alice", "Coached on omega-3 fatty acids");

        List<String> results = tool.lookupNotes("alice", "omega-3");

        assertEquals(1, results.size(), "Should find 1 matching note");
        assertTrue(results.getFirst().contains("omega-3"));
    }

    @Test
    void lookupNotes_nonMatchingQuery_returnsEmptyList() {
        var tool = new MemoryTool();

        tool.storeMemory("alice", "Coached on vitamin D");

        List<String> results = tool.lookupNotes("alice", "creatine");

        assertTrue(results.isEmpty(), "Non-matching query should return empty list");
    }

    @Test
    void lookupNotes_unknownUser_returnsEmptyList() {
        var tool = new MemoryTool();
        // No notes stored for "bob"
        List<String> results = tool.lookupNotes("bob", "omega-3");

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Unknown user should return empty list");
    }

    @Test
    void lookupNotes_multipleNotes_allMatchingReturned() {
        var tool = new MemoryTool();

        tool.storeMemory("alice", "Coached on omega-3 fatty acids");
        tool.storeMemory("alice", "Omega-3 follow-up: check EPA:DHA ratio");
        tool.storeMemory("alice", "Coached on magnesium");

        List<String> results = tool.lookupNotes("alice", "omega");

        assertEquals(2, results.size(), "Both omega-3 notes should be returned");
    }

    @Test
    void storeMemory_multiUser_isolatedByUserId() {
        var tool = new MemoryTool();

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
        var tool = new MemoryTool();

        tool.storeMemory("alice", "Note 1");
        tool.storeMemory("alice", "Note 2");

        List<String> results = tool.lookupNotes("alice", "");

        assertEquals(2, results.size(), "Blank query should return all notes");
    }

    @Test
    void getAllNotes_returnsAllNotesRegardlessOfQuery() {
        var tool = new MemoryTool();

        tool.storeMemory("carol", "Topic A");
        tool.storeMemory("carol", "Topic B");

        List<String> all = tool.getAllNotes("carol");

        assertEquals(2, all.size());
    }
}
