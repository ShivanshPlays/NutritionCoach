package com.nutritioncoach.api;

import com.nutritioncoach.model.ResearchBrief;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 1: Structured output with a versioned prompt template
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This controller demonstrates two Phase 1 concepts together:
 *   1. Versioned prompt templates (loaded from the classpath at startup)
 *   2. Structured output -- the model returns a typed Java record, not free text
 *
 * -- Versioned prompt templates ----------------------------------------
 *
 * MERN/Next.js analogy:
 *   In a Mastra/Vercel AI SDK project you would store prompts in separate files:
 *     // prompts/research-system.ts
 *     export const RESEARCH_SYSTEM = `You are an expert analyst...`
 *   Here we do exactly the same: prompts live in src/main/resources/prompts/
 *   as .st (StringTemplate) files, NOT embedded in Java code. Benefits:
 *     - Version-controlled independently of business logic
 *     - Easy to diff and review prompt changes in PRs
 *     - Readable and editable without touching Java
 *
 * @Value("classpath:prompts/research-system.st") Resource:
 *   Spring injects the file as a `Resource` (similar to fs.readFileSync) but
 *   resolved from the JAR classpath at startup, not the raw filesystem.
 *
 * -- Structured output with .entity() ---------------------------------
 *
 * MERN/Next.js / Mastra analogy:
 *   // Vercel AI SDK:
 *   const { object } = await generateObject({
 *     model, schema: researchBriefSchema, prompt: `Research: ${topic}`
 *   })
 *
 *   // Spring AI equivalent:
 *   chatClient.prompt().system(systemPrompt)
 *     .user(u -> u.text("Research: {topic}").param("topic", topic))
 *     .call().entity(ResearchBrief.class)
 *
 *   Spring AI inspects ResearchBrief via reflection, generates a JSON Schema,
 *   appends format instructions to the prompt, calls the model, then parses
 *   the JSON response back into a ResearchBrief instance automatically.
 *   No manual JSON.parse() or .safeParse() needed.
 *
 * -- Prompt parameter substitution ------------------------------------
 *
 *   .user(u -> u.text("...{topic}").param("topic", value)) is Spring AI's
 *   template parameter syntax. Equivalent to a JS template literal:
 *     `Research the following topic: ${topic}`
 *   Parameters are substituted server-side, not interpolated via string concat,
 *   which provides a layer of protection against prompt injection.
 *
 * Book ref: Chapter 3 -- Writing Great Prompts
 *   System prompt as a versioned file, explicit formatting instructions,
 *   and structured user message with parameters maps to the book's
 *   "seed crystal + system prompt + schema" approach.
 *
 * Book ref: Chapter 5 -- Structured Output
 *   .entity(ResearchBrief.class) is the direct Java equivalent of
 *   generateObject() + Zod schema from the book.
 *
 * Endpoint:
 *   POST /api/research-summary  body: { "topic": "..." }  ->  ResearchBrief JSON
 */
@RestController
@RequestMapping("/api")
public class ResearchController {

    private final ChatClient chatClient;

    // System prompt loaded ONCE from classpath at startup.
    // MERN analogy: reading a template file with fs.readFileSync at module load.
    private final String researchSystemPrompt;

    public ResearchController(
            ChatClient chatClient,
            // @Value injects a Spring Resource (classpath file) by path.
            // MERN analogy: similar to process.env, but for resource paths.
            @Value("classpath:prompts/research-system.st") Resource promptResource) {
        this.chatClient = chatClient;
        try {
            this.researchSystemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load research-system.st prompt template", e);
        }
    }

    // -- POST /api/research-summary ------------------------------------

    @PostMapping("/research-summary")
    public ResearchBrief researchSummary(@RequestBody @Valid ResearchRequest request) {
        return chatClient.prompt()
                // System prompt from versioned template file, not a hardcoded string.
                // Book ref: Ch 3 -- system prompt is the "seed crystal" for the model's role.
                .system(researchSystemPrompt)
                // Parameter substitution: {topic} filled at call time.
                // Safer than string concatenation; guards against prompt injection.
                .user(u -> u
                        .text("Research the following nutrition topic and provide a thorough, " +
                              "structured analysis: {topic}")
                        .param("topic", request.topic()))
                .call()
                // entity() = structured output.
                // Spring AI: reflect ResearchBrief -> JSON Schema -> prompt append -> parse.
                // MERN: generateObject({ schema: researchBriefSchema })
                // Book ref: Ch 5 -- structured output makes agent pipelines reliable.
                .entity(ResearchBrief.class);
    }

    // -- DTO -----------------------------------------------------------
    // MERN analogy: interface ResearchRequest { topic: string }
    record ResearchRequest(@NotBlank(message = "topic must not be blank") String topic) {}
}
