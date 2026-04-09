package com.nutritioncoach.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * MemoryService — Phase 4: JPA-backed memory persistence
 * ══════════════════════════════════════════════════════════════════════════
 *
 * This service replaces the in-memory ConcurrentHashMap in MemoryTool with
 * real database persistence.  It is the single source of truth for all
 * memory reads and writes in the application.
 *
 * Three memory types (Book ref: Chapter 7 — Memory):
 *   saveMessage / getRecentMessages  → short-term / conversation window
 *   saveNote / findNotes             → long-term / episodic (agent notes)
 *   UserProfile (via UserProfileRepository) → long-term / semantic
 *
 * MERN/Next.js analogy:
 *   This is equivalent to a Next.js service layer module or a Mastra Memory
 *   class backed by LibSQLStore:
 *     const memory = new Memory({ storage: new LibSQLStore({ url }) })
 *     await memory.saveMessages({ messages })
 *     await memory.query({ threadId, query })
 *   Here Spring's @Service + JPA repositories play the same role.
 *
 * Deduplication strategy for saveNote():
 *   1. existsByUserIdAndContent()  — app-level check (fast path, avoids exception)
 *   2. UNIQUE constraint (V2 migration) — DB-level guard against race conditions
 *   3. DataIntegrityViolationException catch — handles the rare concurrent race
 *   This three-layer approach mirrors the "defence in depth" principle.
 *
 * @Transactional:
 *   Each write method runs in a DB transaction.  If anything fails mid-method
 *   the whole thing rolls back — exactly like using a Prisma `$transaction([...])`.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    // Maximum conversation window size injected into prompts.
    // Configurable in application.yml (app.agent.memory.window-size).
    static final int DEFAULT_WINDOW = 8;

    private final ConversationMessageRepository messageRepo;
    private final AgentNoteRepository noteRepo;
    private final UserProfileRepository profileRepo;

    // Constructor injection — MERN analogy: const memoryService = new MemoryService({ db })
    public MemoryService(ConversationMessageRepository messageRepo,
                         AgentNoteRepository noteRepo,
                         UserProfileRepository profileRepo) {
        this.messageRepo = messageRepo;
        this.noteRepo = noteRepo;
        this.profileRepo = profileRepo;
    }

    // ── Conversation (short-term memory) ──────────────────────────────────

    /**
     * Persist one conversation turn.
     *
     * @param userId  the user who owns this message
     * @param role    'user' | 'assistant' | 'tool'
     * @param content the message text
     *
     * MERN analogy: prisma.conversationMessage.create({ data: { userId, role, content } })
     *
     * Book ref: Chapter 7 — Memory
     *   Saving every turn enables the conversation window injection in the next call.
     */
    @Transactional
    public void saveMessage(String userId, String role, String content) {
        messageRepo.save(new ConversationMessage(userId, role, content));
    }

    /**
     * Return the last {@code n} messages for a user in chronological order
     * (oldest → newest), ready to be formatted and injected into a prompt.
     *
     * @param userId the user whose history to fetch
     * @param n      maximum number of messages to return
     * @return messages sorted oldest-first for natural reading order
     *
     * MERN analogy:
     *   const messages = await prisma.conversationMessage.findMany({
     *     where: { userId }, orderBy: { createdAt: 'desc' }, take: n
     *   })
     *   messages.reverse()  // oldest first
     */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getRecentMessages(String userId, int n) {
        List<ConversationMessage> desc = messageRepo
                .findTopNByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, n));
        // Reverse so the slice reads oldest → newest (natural conversation order).
        // Used in ChatController to inject into the system prompt for context.
        Collections.reverse(desc);
        return desc;
    }

    // ── Agent notes (long-term / episodic memory) ────────────────────────

    /**
     * Persist a free-text agent note — idempotent (duplicate content is silently ignored).
     *
     * Dedup logic:
     *   1. Check existsByUserIdAndContent — avoids the insert entirely on warm path.
     *   2. The DB UNIQUE constraint (V2 migration) is the final safety net.
     *   3. DataIntegrityViolationException is swallowed so the caller is unaffected.
     *
     * @param userId   the user this note belongs to
     * @param noteType optional classifier ('coaching', 'research', etc.)
     * @param content  the note text
     *
     * MERN analogy: prisma.agentNote.upsert({ where: {uq_...}, create: {...}, update: {} })
     */
    @Transactional
    public void saveNote(String userId, String noteType, String content) {
        if (content == null || content.isBlank()) return;
        // App-level existence check (fast path — avoids DB exception on duplicates).
        if (noteRepo.existsByUserIdAndContent(userId, content)) {
            log.debug("MemoryService.saveNote: duplicate note ignored for user={}", userId);
            return;
        }
        try {
            noteRepo.save(new AgentNote(userId, noteType, content));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests raced past the existence check — both safe.
            log.debug("MemoryService.saveNote: concurrent duplicate ignored for user={}", userId);
        }
    }

    /**
     * Retrieve notes for a user matching a keyword query (case-insensitive substring).
     * Returns all notes when query is blank.
     *
     * Phase 10 replaces this with pgvector similarity search.
     *
     * @param userId the user whose notes to search
     * @param query  case-insensitive substring; empty/null returns all notes
     * @return matching note content strings, newest-first
     *
     * MERN analogy: prisma.agentNote.findMany({ where: { userId, content: { contains: query } } })
     */
    @Transactional(readOnly = true)
    public List<String> findNotes(String userId, String query) {
        List<AgentNote> notes = (query == null || query.isBlank())
                ? noteRepo.findByUserIdOrderByCreatedAtDesc(userId)
                : noteRepo.searchByContent(userId, query);
        return notes.stream().map(AgentNote::getContent).toList();
    }
}
