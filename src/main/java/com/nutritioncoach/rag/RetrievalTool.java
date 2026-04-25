package com.nutritioncoach.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — RetrievalTool
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Responsibility: given a query string, retrieve the top-K most relevant
 * document chunks from the VectorStore and return them as a single
 * context block ready to inject into a prompt.
 *
 * PIPELINE POSITION:
 *   ResearchAgent.gatherFacts(query)
 *     └─► RetrievalTool.retrieveContext(query)      ← this class
 *           └─► VectorStore.similaritySearch(request)
 *                 Result: top-K chunks, highest cosine score first
 *     └─► Inject chunks into LLM prompt
 *     └─► LLM synthesises ResearchBrief grounded in retrieved docs
 *
 * MERN/Next.js analogy (Mastra vectorQueryTool):
 *   export const retrievalTool = createTool({
 *     id: 'retrieveContext',
 *     description: 'Fetch top-K relevant doc chunks for a query',
 *     execute: async ({ query }) => vectorStore.similaritySearch(query, 5)
 *   })
 *
 * Why not register as an Embabel @Action?
 *   RetrievalTool is called explicitly in ResearchAgent.gatherFacts() before
 *   the LLM call — the pre-fetch (deterministic tool call) pattern used
 *   throughout this project.  It is a @Component, not an @Agent/@Action,
 *   so Embabel does not include it in GOAP planning.
 *
 * noOp() factory:
 *   Used by ResearchAgent's no-arg constructor (test-friendly) so unit tests
 *   don't need a real VectorStore.  Returns an instance that always returns
 *   an empty context string without touching the VectorStore.
 *   MERN analogy: jest.fn().mockResolvedValue([]) — stub that returns nothing.
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Top-K retrieval with cosine similarity is the baseline.  Reranking with
 *    a cross-encoder model can dramatically improve precision at small K."
 *
 * Book ref: Chapter 20 — RAG: Synthesis
 *   "Inject retrieved context in a clearly labelled section of the prompt so
 *    the model knows exactly which facts to use and doesn't hallucinate."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
public class RetrievalTool {

    // Default top-K — configurable in Phase 10 level 2 via @Value
    private static final int DEFAULT_TOP_K = 5;

    // Sentinel: returned by noOp() instances so tests always get a clean state
    static final String NO_CONTEXT = "";

    private final SimpleVectorStore vectorStore;
    private final int topK;

    @Autowired
    public RetrievalTool(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.topK = DEFAULT_TOP_K;
    }

    // Package-visible constructor for tests that override topK
    RetrievalTool(SimpleVectorStore vectorStore, int topK) {
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    /**
     * Retrieve the top-K relevant chunks for the query and join them into a
     * single string block separated by dashes.
     *
     * Returns an empty string when no documents have been ingested yet,
     * so callers can decide how to handle "no context" gracefully.
     *
     * MERN analogy:
     *   const docs = await vectorStore.similaritySearch(query, topK)
     *   return docs.map(d => d.pageContent).join('\n---\n')
     *
     * @param query the user's nutrition question / topic
     * @return retrieved context as a multi-paragraph string, or "" if empty
     *
     * Book ref: Chapter 19 — RAG: Retrieval & Reranking
     *   "At retrieval time, embed the query with the SAME model used at
     *    ingestion time — mismatched models destroy similarity scores."
     */
    public String retrieveContext(String query) {
        if (vectorStore == null || query == null || query.isBlank()) return NO_CONTEXT;

        // SearchRequest: embed the query and compute cosine similarity against all stored vectors
        // MERN analogy: vectorStore.similaritySearch(query, topK)
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) return NO_CONTEXT;

        // Concatenate chunk content, separated by a dash delimiter.
        // The delimiter helps the LLM distinguish between source chunks.
        // Book ref: Ch 20 — label the context block so the model sees clear anchors.
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * No-op factory — returns a RetrievalTool instance backed by a null VectorStore.
     * Used by ResearchAgent's no-arg constructor so existing unit tests don't
     * need to wire a VectorStore.  retrieveContext() always returns "".
     *
     * MERN analogy: const mockRetrieval = { retrieveContext: () => '' }
     */
    public static RetrievalTool noOp() {
        return new RetrievalTool(null) {
            @Override
            public String retrieveContext(String query) {
                return NO_CONTEXT;
            }
        };
    }
}
