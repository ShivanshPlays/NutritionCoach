package com.nutritioncoach.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ═══════════════════════════════════════════════════════════════
 * ChatClient Configuration
 * ═══════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   In a Next.js project you might do:
 *     // lib/ai.ts
 *     export const openai = createOpenAI({ apiKey: process.env.OPENAI_API_KEY })
 *     export const client = openai('gpt-4o')
 *   This class does the same thing for Spring: it creates a single shared
 *   ChatClient bean that every controller/service can request via constructor injection.
 *
 * What is a @Bean?
 *   A method annotated with @Bean inside a @Configuration class is Spring's
 *   equivalent of a factory function that returns a shared singleton.
 *   Spring calls the method once, stores the result, and injects it wherever
 *   the return type (ChatClient) is requested as a constructor argument.
 *
 *   Node.js analogy: a module-level export that is imported everywhere:
 *     export const chatClient = buildClient()   // called once at startup
 *
 * What is ChatClient?
 *   Spring AI's high-level fluent API for talking to any LLM.
 *   Think of it as the Java equivalent of Vercel AI SDK's `generateText()` +
 *   `generateObject()` + `streamText()` combined into one object.
 *
 *   Fluent API shape:
 *     chatClient.prompt()
 *       .system("...")       // ← same as the `system` param in generateText()
 *       .user("...")         // ← same as the `messages[{role:'user'}]` array
 *       .call()              // ← triggers the HTTP request to the model
 *       .content()           // ← returns raw String (like `text` in generateText)
 *       // OR
 *       .entity(MyRecord.class)  // ← typed structured output (like generateObject)
 *
 * Book ref: Chapter 2 — Choosing a Provider & Model
 *   The ChatClient abstracts the provider choice.  Swapping Gemini for GPT-4o
 *   or Claude is just a dependency + config change — this class stays the same.
 *
 * Phase 7+: this will be extended to produce multiple named ChatClient beans
 *   (one per user tier / model) to implement dynamic agent model selection.
 */
@Configuration
public class ChatClientConfig {

    // MERN analogy: @Bean method = module-level factory export
    // Spring calls this once; the ChatClient is then injectable everywhere.
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        // Builder is auto-configured by Spring AI using application.yml settings.
        // No default system prompt here — each controller sets its own context.
        return builder.build();
    }
}
