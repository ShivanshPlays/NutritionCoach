package com.nutritioncoach.model;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ResearchBrief — Structured Output Record
 * ══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   This is a TypeScript interface/type, but compiled and runtime-enforced:
 *     interface ResearchBrief {
 *       topic: string
 *       keyFindings: string[]
 *       risks: string[]
 *       nextQuestions: string[]
 *     }
 *   In Vercel AI SDK you'd pass a matching Zod schema to `generateObject()`:
 *     const schema = z.object({
 *       topic: z.string(),
 *       keyFindings: z.array(z.string()),
 *       risks: z.array(z.string()),
 *       nextQuestions: z.array(z.string()),
 *     })
 *
 * What is a Java `record`?
 *   Introduced in Java 16. A record is an immutable data class with
 *   auto-generated constructor, getters, equals/hashCode/toString.
 *   It maps perfectly to an immutable TypeScript type.
 *
 * How Spring AI uses this record for structured output:
 *   When you call `.entity(ResearchBrief.class)`, Spring AI:
 *     1. Inspects the record's field names and types via reflection.
 *     2. Builds a JSON Schema describing the expected output shape.
 *     3. Appends instructions to the prompt telling the model to respond
 *        with JSON matching that schema.
 *     4. Parses the model's JSON response into a ResearchBrief instance
 *        using Jackson (the standard Java JSON library, like `JSON.parse`).
 *
 * Book ref: Chapter 5 — Structured Output
 *   The book explains why forcing structured output is essential for
 *   building reliable agent pipelines — free text is unparseable downstream.
 *   This record is the Java equivalent of the Zod schema + `generateObject()`
 *   pattern from the Mastra/Vercel AI SDK world.
 *
 * Phase 2+: This record will be produced by `ResearchAgent @Action` instead
 *   of directly by the controller, but the schema stays the same.
 */
public record ResearchBrief(

        /** The nutrition topic that was researched. */
        String topic,

        /** Key facts, scientific findings, and mechanisms. */
        List<String> keyFindings,

        /** Potential risks, contraindications, or common misconceptions. */
        List<String> risks,

        /** Follow-up questions that would deepen understanding of the topic. */
        List<String> nextQuestions
) {}
