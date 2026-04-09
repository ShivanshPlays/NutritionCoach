package com.nutritioncoach.api;

import com.nutritioncoach.memory.ConversationMessage;
import com.nutritioncoach.memory.MemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 0-4 REST endpoints: health check + Gemini chat with memory
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy -- @RestController + @RequestMapping:
 *   This class is equivalent to a Next.js API route file OR an Express router:
 *     // Next.js App Router:  app/api/chat/route.ts
 *     export async function POST(req) { ... }
 *
 *     // Express:
 *     router.get('/health', (req, res) => res.json({ status: 'ok' }))
 *     router.post('/chat', async (req, res) => { ... })
 *
 *   @RestController = @Controller + @ResponseBody combined.
 *     @Controller  -> marks the class as a web component (Spring finds it via scan)
 *     @ResponseBody -> auto-serialises return values to JSON (like res.json())
 *
 *   @RequestMapping("/api") sets the base path for all methods in this class,
 *   equivalent to app.use('/api', router) in Express.
 *
 * Constructor Dependency Injection:
 *   In Node you'd: const chatClient = new ChatClient(config)
 *   In Spring you declare the dep in the constructor, Spring injects it.
 *   No `new`, no manual wiring -- Spring owns the object lifecycle.
 *
 * @RequestBody -- MERN analogy: req.body in Express, await request.json() in Next.js.
 * @Valid       -- MERN analogy: Zod .parse() / .safeParse().
 *
 * Phase 4 addition — conversation history window:
 *   Each POST /api/chat now:
 *     1. Saves the user turn to conversation_message (JPA / H2 → PostgreSQL later).
 *     2. Fetches the last HISTORY_WINDOW turns from DB.
 *     3. Formats them as a history block injected into the system prompt.
 *     4. Calls Gemini with the enriched context.
 *     5. Saves the assistant reply.
 *
 *   MERN analogy:
 *     await db.message.create({ data: { userId, role: 'user', content } })
 *     const history = await db.message.findMany({ where: { userId }, orderBy: { createdAt: 'desc' }, take: 8 })
 *     history.reverse()
 *     const result = await generateText({ system: buildSystemPrompt(history), prompt: message })
 *     await db.message.create({ data: { userId, role: 'assistant', content: result.text } })
 *
 * Book ref: Chapter 2 -- Choosing a Provider & Model
 *   Verifying provider connectivity end-to-end is the first milestone.
 *
 * Book ref: Chapter 3 -- Writing Great Prompts
 *   The system prompt uses the "role + scope + context" pattern; history block is
 *   the "context" section added in Phase 4.
 *
 * Book ref: Chapter 7 -- Memory
 *   The conversation window (last N turns) is the simplest form of short-term
 *   memory.  This phase implements the "sliding window" strategy the book describes.
 *
 * Endpoints:
 *   GET  /api/health  -- liveness probe
 *   POST /api/chat    -- Gemini round-trip with persistent conversation window
 */
@RestController
// MERN analogy: like express.Router() then app.use('/api', router)
@RequestMapping("/api")
public class ChatController {

    // Number of previous turns to inject into each prompt (sliding window).
    // Book ref: Chapter 7 — Memory — "context window" strategy.
    // MERN analogy: const HISTORY_WINDOW = 8 in your AI route module.
    private static final int HISTORY_WINDOW = 8;

    // Phase 4: hard-coded until Phase 6 adds auth.
    // MERN analogy: const DEFAULT_USER_ID = 'default' in a userId middleware placeholder.
    private static final String DEFAULT_USER_ID = "default";

    private final ChatClient chatClient;
    private final MemoryService memoryService;

    // Spring injects both beans automatically via constructor injection.
    // MERN analogy: destructuring deps from a DI container or passing them explicitly.
    public ChatController(ChatClient chatClient, MemoryService memoryService) {
        this.chatClient = chatClient;
        this.memoryService = memoryService;
    }

    // -- GET /api/health -----------------------------------------------
    // MERN analogy: router.get('/health', (req, res) => res.json({ status: 'ok' }))
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    // -- POST /api/chat ------------------------------------------------
    //
    // ChatClient fluent chain -- equivalent to Vercel AI SDK:
    //   generateText({
    //     model: gemini('gemini-2.0-flash'),
    //     system: buildSystemPrompt(history),
    //     prompt: message,
    //   })
    //
    // .system()   <- the `system` field in generateText()
    // .user()     <- messages[{role:'user', content}]
    // .call()     <- triggers the HTTP request to Gemini API
    // .content()  <- returns raw String (like `result.text` in Vercel AI SDK)
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        // 1. Persist the incoming user turn.
        // MERN analogy: await db.message.create({ data: { userId, role:'user', content } })
        memoryService.saveMessage(DEFAULT_USER_ID, "user", request.message());

        // 2. Load the last HISTORY_WINDOW turns (oldest → newest).
        // MERN analogy: history = await memory.getRecentMessages(userId, HISTORY_WINDOW)
        List<ConversationMessage> history = memoryService.getRecentMessages(DEFAULT_USER_ID, HISTORY_WINDOW);

        // 3. Build system prompt with optional history block.
        // Book ref: Chapter 3 — Writing Great Prompts
        //   Injecting history as a well-labelled "Conversation history:" section
        //   follows the "structured context" pattern from the book.
        String systemPrompt = buildSystemPrompt(history);

        // 4. Call the LLM.
        String reply = chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
                .call()
                .content();

        // 5. Persist the assistant reply so the next call can reference it.
        // MERN analogy: await db.message.create({ data: { userId, role:'assistant', content: reply } })
        memoryService.saveMessage(DEFAULT_USER_ID, "assistant", reply);

        return new ChatResponse(reply);
    }

    // -- Helpers -------------------------------------------------------

    /**
     * Build the system prompt string, optionally prepended with the
     * formatted conversation history block.
     *
     * Keeping history in the system prompt (rather than as separate Message
     * objects) is the simplest approach for now; Phase 10 will move to a
     * proper multi-turn message list when pgvector retrieval is introduced.
     *
     * MERN analogy:
     *   function buildSystemPrompt(history) {
     *     const historyBlock = history.map(m => `${m.role}: ${m.content}`).join('\n')
     *     return `You are a helpful nutrition coach...\n\nConversation history:\n${historyBlock}`
     *   }
     */
    private String buildSystemPrompt(List<ConversationMessage> history) {
        String base = "You are a helpful nutrition coach assistant. " +
                "Provide concise, evidence-based answers about nutrition and healthy eating.";
        if (history.isEmpty()) {
            return base;
        }
        StringBuilder historyBlock = new StringBuilder();
        for (ConversationMessage msg : history) {
            String label = "user".equals(msg.getRole()) ? "User" : "Assistant";
            historyBlock.append(label).append(": ").append(msg.getContent()).append("\n");
        }
        return base + "\n\nConversation history (oldest to newest):\n" + historyBlock.toString().trim();
    }

    // -- DTOs (Data Transfer Objects) ----------------------------------
    //
    // MERN analogy: TypeScript interfaces for request/response shapes.
    //   interface ChatRequest { message: string }
    //   interface ChatResponse { reply: string }
    //
    // Java `record` = immutable TS type with auto-generated constructor + getters.
    // Jackson (Spring's JSON library) serialises/deserialises records automatically.
    // @NotBlank = Bean Validation (JSR-380), equivalent to z.string().min(1) in Zod.

    record HealthResponse(String status) {}

    record ChatRequest(@NotBlank(message = "message must not be blank") String message) {}

    record ChatResponse(String reply) {}
}
