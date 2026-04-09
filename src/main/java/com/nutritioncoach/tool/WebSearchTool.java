package com.nutritioncoach.tool;

import org.springframework.stereotype.Component;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * WebSearchTool — Fetch supplementary nutrition facts from the web
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Tool profile (Phase 3 documentation requirement):
 *   Input:   query (String) — the topic or ingredient to look up
 *   Output:  String — a block of text summarising search results
 *   Read-only or mutating? READ-ONLY — makes no state changes
 *   Safe for the model to call without human confirmation? YES — pure lookup
 *
 * MERN/Next.js analogy:
 *   In Mastra you'd write:
 *     const webSearchTool = createTool({
 *       id: 'webSearch',
 *       description: 'Search the web for nutrition information',
 *       inputSchema: z.object({ query: z.string() }),
 *       execute: async ({ context }) => fetch(SEARCH_API + context.query)
 *     })
 *   Here, the tool is a Spring @Component.  The agent receives it via
 *   constructor injection (Spring DI) rather than a tool registry object.
 *
 * Implementation notes:
 *   Phase 3 — STUB implementation.  Returns keyword-keyed canned responses
 *   to allow end-to-end testing without an external search API.
 *   Phase 10 (RAG) will replace this with real document retrieval against
 *   a pgvector store, or a live web-search API call.
 *
 *   This stub approach is intentional: the tool interface (method signature)
 *   stays identical when the backing implementation changes.  Callers
 *   (CoachAgent) never need updating — only this class changes.
 *   MERN analogy: swapping a mocked fetch() for a real API call in the
 *   execute block of a Mastra tool without changing the tool's contract.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "Design tools to be composable and idempotent. A search tool should
 *   return the same results for the same query (within a session) and
 *   never mutate external state."
 *
 * Book ref: Chapter 10 — Third-Party Tools & Integrations
 *   This pattern — wrapping an external service behind a clean interface —
 *   is exactly how the book recommends integrating third-party capabilities.
 *   The stub simulates a search API; replacing it with Brave, Bing, or
 *   Tavily is a one-class change.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class WebSearchTool {

    /**
     * Search for relevant nutrition information.
     *
     * @param query the ingredient, nutrient, or topic to research
     * @return a plain-text block summarising what a web search would return
     *
     * MERN analogy: the `execute` function inside a Mastra createTool() call.
     * In production this would be:
     *   return httpClient.get(SEARCH_API_URL + URLEncoder.encode(query, UTF_8))
     */
    public String searchWeb(String query) {
        // Phase 3 stub: keyword-based canned responses.
        // Real implementation (Phase 10): call a search API or vector retrieval.
        String q = query.toLowerCase();

        if (contains(q, "omega-3", "fish oil", "epa", "dha")) {
            return """
                    [WebSearch: omega-3 / fish oil]
                    EPA and DHA are long-chain omega-3 fatty acids found in fatty fish (salmon, mackerel,
                    sardines) and algae-based supplements. A meta-analysis of 13 RCTs (JAMA 2019) found
                    1–2 g EPA+DHA/day reduced triglycerides by ~20% and modestly reduced cardiovascular
                    events. The American Heart Association recommends 1 g/day for established coronary
                    artery disease. Plant sources (flaxseed, chia) provide ALA, which converts to EPA/DHA
                    at only 5–10% efficiency in humans.
                    """;
        } else if (contains(q, "vitamin d", "cholecalciferol", "calciferol")) {
            return """
                    [WebSearch: vitamin D]
                    Vitamin D3 (cholecalciferol) is synthesised in skin from UVB exposure and obtained
                    from fatty fish, egg yolks, and fortified foods. The Institute of Medicine sets the
                    RDA at 600–800 IU/day for adults, but many practitioners target 1000–2000 IU/day
                    for 25-OH-D serum levels of 40–60 ng/mL. Deficiency is linked to bone loss,
                    impaired immunity, and higher COVID-19 severity. Toxicity (>100 ng/mL) can cause
                    hypercalcaemia at chronic doses >10,000 IU/day.
                    """;
        } else if (contains(q, "protein", "amino acid", "leucine", "whey", "casein")) {
            return """
                    [WebSearch: protein / amino acids]
                    The International Society of Sports Nutrition recommends 1.4–2.0 g protein/kg/day
                    for exercising adults. Leucine (2–3 g per meal) acts as the key mTOR trigger for
                    muscle protein synthesis. Whey is fast-absorbing and high in leucine (~11%); casein
                    is slow-digesting and ideal pre-sleep. Plant proteins (pea, soy, rice blend) can
                    match whey when combined to cover all essential amino acids.
                    """;
        } else if (contains(q, "magnesium")) {
            return """
                    [WebSearch: magnesium]
                    Magnesium participates in >300 enzymatic reactions. RDA: 310–420 mg/day. Best food
                    sources: pumpkin seeds (156 mg/oz), dark chocolate (64 mg/oz), almonds, spinach.
                    Glycinate and malate forms show superior absorption over oxide. Deficiency is
                    associated with depression, poor sleep, hypertension, and insulin resistance.
                    Supplementation (200–400 mg/day) improves sleep quality in deficient individuals.
                    """;
        } else if (contains(q, "creatine")) {
            return """
                    [WebSearch: creatine]
                    Creatine monohydrate is the most studied sports supplement. Loading: 20 g/day × 5
                    days; maintenance: 3–5 g/day. Increases phosphocreatine stores, improving high-
                    intensity exercise output by ~5–15%. Cochrane review 2021: significant lean mass
                    gain vs placebo. Safe for healthy kidneys. Vegetarians/vegans respond more due to
                    lower baseline muscle creatine. No evidence of hair-loss risk at standard doses.
                    """;
        } else if (contains(q, "gut", "microbiome", "probiotics", "prebiotic", "fibre", "fiber")) {
            return """
                    [WebSearch: gut microbiome / probiotics]
                    The gut microbiome comprises ~38 trillion microorganisms. Diet is the #1 modifiable
                    factor. High dietary fibre (25–38 g/day) feeds Bifidobacterium and Lactobacillus,
                    producing short-chain fatty acids (butyrate, propionate) that strengthen the
                    intestinal lining. Lactobacillus rhamnosus GG and Saccharomyces boulardii have the
                    strongest evidence for antibiotic-associated diarrhoea prevention. Fermented foods
                    (yoghurt, kefir, kimchi) increase microbiome diversity within 6 weeks.
                    """;
        } else {
            // Fallback for any unrecognised query
            return """
                    [WebSearch: %s]
                    General nutrition guidance: focus on a diverse whole-food diet rich in vegetables
                    (5+ servings/day), adequate protein (0.8–1.6 g/kg/day), healthy fats (olive oil,
                    nuts, avocado), and minimise ultra-processed foods. Stay hydrated (35 ml/kg/day).
                    Consult a registered dietitian for personalised advice tailored to your health
                    status, medications, and goals.
                    """.formatted(query);
        }
    }

    // ── helper ────────────────────────────────────────────────────────────

    //The ... in String... needles is called a "varargs" (variable arguments) parameter in Java.

    //It means you can pass any number of String arguments (including zero) to the method, and inside the method, they are available as an array (String[] needles).
    
    private boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
