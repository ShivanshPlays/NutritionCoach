package com.nutritioncoach.config;

import com.nutritioncoach.rag.KeywordEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — Vector store configuration
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Why a @Configuration class and not auto-configuration?
 *   Spring AI does NOT auto-configure SimpleVectorStore.  Only specific
 *   backends (PgVector, Chroma, Pinecone, etc.) get auto-configured via their
 *   own starters.  For SimpleVectorStore we provide the bean explicitly.
 *
 * VectorStore interface (Spring AI):
 *   • add(List<Document>)            — index new documents
 *   • similaritySearch(SearchRequest)— top-K retrieval
 *   • delete(List<String>)           — remove by ID
 *   MERN analogy: a Pinecone / Weaviate / Supabase Vector client providing
 *   the same upsert/query/delete API.
 *
 * SimpleVectorStore:
 *   An in-memory implementation — perfect for dev and automated tests.
 *   Every document is stored in a ConcurrentHashMap; similarity search
 *   does a brute-force cosine comparison over all stored vectors.
 *   Data is lost on application restart; no schema migration needed.
 *
 * SWITCHING TO PgVectorStore (Phase 10, level 2):
 *   1. Uncomment the PostgreSQL driver in pom.xml
 *   2. Un-comment the pgvector starter below (once added to pom.xml):
 *        spring-ai-pgvector-store-spring-boot-starter
 *   3. Replace the @Bean below with:
 *        @Bean
 *        public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel em) {
 *            return new PgVectorStore(jdbcTemplate, em);
 *        }
 *   4. Add Flyway V4 migration:
 *        CREATE EXTENSION IF NOT EXISTS vector;
 *        CREATE TABLE vector_store (
 *            id UUID PRIMARY KEY,
 *            content TEXT,
 *            metadata JSON,
 *            embedding vector(384)
 *        );
 *        CREATE INDEX ON vector_store USING ivfflat (embedding vector_cosine_ops);
 *   5. Switch datasource URL in application.yml to PostgreSQL
 *
 * MERN analogy:
 *   // Supabase (Node.js) — switching from memory to pgvector:
 *   const vectorStore = await SupabaseVectorStore.fromDocuments(docs, embeddings, { client })
 *   // vs in-memory:
 *   const vectorStore = await MemoryVectorStore.fromDocuments(docs, embeddings)
 *
 * Book ref: Chapter 18 — RAG: Embedding & Indexing
 *   "The vector DB is where embedding vectors live at query-time.
 *    Start with in-memory for development; switch to pgvector for production
 *    persistence and approximate nearest-neighbour (ANN) indexing."
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "SimpleVectorStore uses exact cosine similarity (O(n)).
 *    PgVector with ivfflat index uses ANN (O(log n)) for large corpora."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Configuration
public class VectorStoreConfig {

    /**
     * In-memory vector store — dev default.
     *
     * The EmbeddingModel injected here is our @Primary KeywordEmbeddingModel
     * (no API calls, works in tests without any network access).
     * In prod (Phase 10, level 2) swap for a PgVectorStore bean instead.
     *
     * MERN analogy: initialising a MemoryVectorStore from LangChain.js:
     *   const vectorStore = new MemoryVectorStore(embeddingModel)
     */
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        // SimpleVectorStore.builder() is the preferred factory in Spring AI 1.1.x.
        // It wraps the model and sets up the cosine-similarity search engine.
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
