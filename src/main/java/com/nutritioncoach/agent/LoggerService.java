package com.nutritioncoach.agent;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.memory.ConversationMessage;
import com.nutritioncoach.memory.MemoryService;
import com.nutritioncoach.memory.NoteType;
import com.nutritioncoach.memory.UserProfile;
import com.nutritioncoach.memory.UserProfileRepository;
import com.nutritioncoach.model.LoggerInput;
import com.nutritioncoach.model.LoggerSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * Phase 8: LoggerService — orchestrates LoggerAgent + persistence
 * ══════════════════════════════════════════════════════════════════════════
 *
 * This service is the bridge between controllers and LoggerAgent.
 *
 * Responsibilities:
 *   1. Format recent messages into LoggerInput
 *   2. Invoke LoggerAgent via Embabel (1 LLM call)
 *   3. Persist the structured LoggerSummary:
 *        a. compressedSummary → AgentNote (type "summary")
 *        b. extractedGoals    → UserProfile.dietaryGoals (if non-empty)
 *        c. extractedRestrictions → UserProfile.restrictions (if non-empty)
 *        d. keyFacts          → each fact → AgentNote (type "fact")
 *
 * @Async runAsync():
 *   Called from CoachController and FullAdviceController AFTER the advice
 *   is returned to the user.  @Async dispatches this method to a background
 *   thread so it never adds latency to the HTTP response.
 *
 *   MERN analogy:
 *     // Fire-and-forget in Node.js:
 *     res.json(advice)               // respond immediately
 *     setImmediate(() => loggerService.run(userId, messages))  // then log
 *
 * Why this is a @Service and not inside a controller:
 *   Controllers should be thin — request/response only.
 *   LoggerService encapsulates all the logging orchestration so it can
 *   be wired into multiple controllers without code duplication.
 *   MERN analogy: a shared "loggerService.ts" imported by multiple route handlers.
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "A dedicated Logger agent that runs after each turn keeps the
 *    coaching agents focused on their primary task."
 *
 * Book ref: Chapter 7 — Memory
 *   "Extract signal from conversation history and write structured facts
 *    to your long-term store — don't keep raw transcripts."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Service
public class LoggerService {

    private static final Logger log = LoggerFactory.getLogger(LoggerService.class);

    // Embabel runtime — used to invoke LoggerAgent.
    // MERN analogy: const mastra = new Mastra({ agents: [loggerAgent] })
    private final AgentPlatform agentPlatform;

    // MemoryService for saving notes — same abstraction used by MemoryTool.
    // MERN analogy: const memory = new Memory({ storage: store })
    private final MemoryService memoryService;

    // UserProfileRepository for reading/writing UserProfile.
    // LoggerService is the only writer of extracted preferences.
    // MERN analogy: prisma.userProfile
    private final UserProfileRepository profileRepo;

    public LoggerService(AgentPlatform agentPlatform,
                         MemoryService memoryService,
                         UserProfileRepository profileRepo) {
        this.agentPlatform = agentPlatform;
        this.memoryService = memoryService;
        this.profileRepo = profileRepo;
    }

    /**
     * Fire-and-forget: invoke LoggerAgent on recent messages and persist the results.
     *
     * Called after each successful advice-generation request.
     * @Async means the calling thread returns immediately; this runs on a
     * background thread from Spring's task executor pool.
     *
     * @param userId   the user whose conversation is being logged
     * @param messages the last N ConversationMessages (from MemoryService)
     *
     * MERN analogy:
     *   export async function runLogger(userId: string, messages: Message[]) {
     *     const summary = await loggerAgent.generate(messages)
     *     await memory.saveNote(userId, 'summary', summary.compressedSummary)
     *     if (summary.extractedGoals) await db.userProfile.update(...)
     *   }
     */
    @Async
    public void runAsync(String userId, List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("LoggerService.runAsync: no messages for user={}, skipping", userId);
            return;
        }

        try {
            // ── Step 1: Format messages into LoggerInput ──────────────────
            // Convert ConversationMessage entities to "role: content" strings.
            // MERN analogy: messages.map(m => `${m.role}: ${m.content}`)
            List<String> formatted = messages.stream()
                    .map(m -> m.getRole() + ": " + m.getContent())
                    .toList();

            LoggerInput input = new LoggerInput(userId, formatted);
            log.info("LoggerService: invoking LoggerAgent for user={} messages={}", userId, messages.size());

            // ── Step 2: Invoke LoggerAgent (1 LLM call) ───────────────────
            // Embabel auto-selects LoggerAgent because LoggerSummary is its goal type.
            // MERN analogy: const summary = await loggerAgent.generate(input)
            LoggerSummary summary = AgentInvocation
                    .create(agentPlatform, LoggerSummary.class)
                    .invoke(input);

            // ── Step 3: Persist results ───────────────────────────────────

            // 3a. Save compressed session summary as an agent note.
            if (summary.compressedSummary() != null && !summary.compressedSummary().isBlank()) {
                memoryService.saveNote(userId, NoteType.SUMMARY, summary.compressedSummary());
            }

            // 3b. Save each key fact as a separate agent note.
            // Deduplicated by MemoryService (idempotent saveNote).
            // MERN analogy: await Promise.all(facts.map(f => memory.saveNote(...)))
            if (summary.keyFacts() != null) {
                for (String fact : summary.keyFacts()) {
                    if (fact != null && !fact.isBlank()) {
                        memoryService.saveNote(userId, NoteType.FACT, fact);
                    }
                }
            }

            // 3c. Update UserProfile with extracted goals / restrictions.
            // Only overwrite if the LLM actually extracted something non-empty.
            // Uses find-or-create pattern so new users get a profile automatically.
            // MERN analogy: prisma.userProfile.upsert({ where: { userId }, update: {...} })
            boolean needsProfileUpdate = (summary.extractedGoals() != null && !summary.extractedGoals().isBlank())
                    || (summary.extractedRestrictions() != null && !summary.extractedRestrictions().isBlank());

            if (needsProfileUpdate) {
                UserProfile profile = profileRepo.findByUserId(userId)
                        .orElseGet(() -> new UserProfile(userId));

                if (summary.extractedGoals() != null && !summary.extractedGoals().isBlank()) {
                    profile.dietaryGoals(summary.extractedGoals());
                }
                if (summary.extractedRestrictions() != null && !summary.extractedRestrictions().isBlank()) {
                    profile.restrictions(summary.extractedRestrictions());
                }
                profileRepo.save(profile);
                log.info("LoggerService: updated UserProfile for user={}", userId);
            }

            log.info("LoggerService: completed for user={} facts={}", userId,
                    summary.keyFacts() != null ? summary.keyFacts().size() : 0);

        } catch (Exception e) {
            // Log and swallow — logging is a background concern. A failure here
            // must NEVER degrade the user-visible advice response.
            // MERN analogy: .catch(err => logger.error('Logger failed', err))
            log.error("LoggerService.runAsync: failed for user={} — {}", userId, e.getMessage(), e);
        }
    }
}
