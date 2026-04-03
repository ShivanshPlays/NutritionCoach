package com.nutritioncoach;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.context.TestPropertySource;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Application Context Smoke Test
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   This is equivalent to a Jest test that imports your Next.js app and
 *   checks it doesn't crash on startup -- like:
 *     it('starts without errors', async () => {
 *       const server = await createServer()
 *       expect(server).toBeDefined()
 *     })
 *
 * @SpringBootTest:
 *   Boots the FULL Spring application context -- all beans, configs, DB, etc.
 *   This is an integration test, not a unit test.  It catches wiring mistakes
 *   (missing beans, bad config) that unit tests can't see.
 *   MERN analogy: like running `npm run build` and catching import errors.
 *
 * @MockBean ChatModel:
 *   Replaces the real Gemini HTTP client with a Mockito mock so the test
 *   doesn't need a live GEMINI_API_KEY or network access.
 *   MERN analogy: jest.mock('../lib/ai') -- swap the real AI SDK for a stub.
 *   Without this, the test would fail unless GEMINI_API_KEY is set in CI.
 *
 * @TestPropertySource:
 *   Injects test-only property values, overriding application.yml.
 *   MERN analogy: setting process.env.GEMINI_API_KEY = 'test' in a Jest setup file.
 *   The placeholder satisfies Spring AI's property binding before the mock kicks in.
 *
 * Book ref: Chapter 27 -- Evaluations Overview
 *   The book stresses that evals and tests matter because AI systems are
 *   non-deterministic. This smoke test is the baseline -- confirm the app
 *   starts before testing any LLM behaviour.
 */
@SpringBootTest
@TestPropertySource(properties = "GEMINI_API_KEY=test-placeholder-do-not-use")
class NutritionCoachApplicationTests {

    // MockBean replaces the real Gemini HTTP client with a no-op stub.
    // This prevents any real API calls during context-load tests.
    @MockBean
    ChatModel chatModel;

    @Test
    void contextLoads() {
        // If the Spring context assembles without throwing, this test passes.
        // No assertions needed -- the test framework counts an exception as failure.
    }
}
