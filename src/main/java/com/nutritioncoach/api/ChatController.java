package com.nutritioncoach.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 0 REST endpoints: health check + raw Gemini chat
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
 *   Think of it as: Spring reads all constructors like an IoC container and
 *   resolves the full dependency graph automatically.
 *
 * @RequestBody -- MERN analogy: req.body in Express, await request.json() in Next.js.
 *   Spring reads the JSON body and deserialises it into the record automatically.
 *
 * @Valid -- MERN analogy: Zod .parse() / .safeParse().
 *   Triggers Bean Validation annotations before the method body runs.
 *   If @NotBlank fails, Spring returns a 400 automatically.
 *
 * Book ref: Chapter 2 -- Choosing a Provider & Model
 *   Verifying the provider connection works end-to-end is the first milestone
 *   the book recommends before building any agent logic.
 *
 * Book ref: Chapter 3 -- Writing Great Prompts
 *   The system prompt here shows the "role + scope + constraints" pattern.
 *
 * Endpoints:
 *   GET  /api/health  -- liveness probe (used by load balancers / k8s)
 *   POST /api/chat    -- raw free-text Gemini round-trip (Phase 0 smoke test)
 */
@RestController
// MERN analogy: like express.Router() then app.use('/api', router)
@RequestMapping("/api")
public class ChatController {

    // MERN analogy: like importing a shared `aiClient` module instance.
    // Spring injects the ChatClient bean built in ChatClientConfig automatically.
    private final ChatClient chatClient;

    // Constructor injection -- preferred over @Autowired field injection.
    // Spring sees this constructor, finds a ChatClient bean, and passes it in.
    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
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
    //     system: 'You are a nutrition coach...',
    //     prompt: message,
    //   })
    //
    // .system()   <- the `system` field in generateText()
    // .user()     <- messages[{role:'user', content}]
    // .call()     <- triggers the HTTP request to Gemini API
    // .content()  <- returns raw String (like `result.text` in Vercel AI SDK)
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        String reply = chatClient.prompt()
                .system("You are a helpful nutrition coach assistant. " +
                        "Provide concise, evidence-based answers about nutrition and healthy eating.")
                .user(request.message())
                .call()
                .content();
        return new ChatResponse(reply);
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
