package com.nutritioncoach.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * NutritionCalcTool — Look up calorie and macro data for a food item
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Tool profile (Phase 3 documentation requirement):
 *   Input:   food (String) — ingredient or dish name
 *            quantity (String) — serving description (e.g. "100g", "1 cup")
 *   Output:  String — formatted macro breakdown
 *   Read-only or mutating? READ-ONLY — pure lookup, no state changes
 *   Safe for the model to call without human confirmation? YES — deterministic
 *
 * MERN/Next.js analogy:
 *   In Mastra this would be a createTool() wrapping a nutrition API:
 *     const nutritionTool = createTool({
 *       id: 'calcNutrition',
 *       execute: async ({ context }) =>
 *         fetch(`https://nutritionix.com/api?q=${context.food}`)
 *     })
 *
 *   Here it is a Spring @Component with a hard-coded macro table (per 100 g).
 *   Phase 10 can upgrade to a real API call (Nutritionix, USDA FoodData Central,
 *   Open Food Facts) without changing the CoachAgent caller.
 *
 * Data source:
 *   Values are approximate USDA FoodData Central figures per 100 g as-consumed.
 *   For serving sizes other than 100 g, values are scaled linearly.
 *   This is intentionally simplified for Phase 3 — a real implementation would
 *   handle complex recipes, raw vs cooked weight, and unit conversions properly.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "Good tools are narrow, deterministic, and easy to test."
 *   This tool returns the exact same string for the same (food, quantity) pair,
 *   making it trivially unit-testable and safe to cache.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class NutritionCalcTool {

    // ── Macro table: food name (normalised) → MacrosPer100g ──────────────

    private record MacrosPer100g(double kcal, double proteinG, double carbG, double fatG, String notes) {}

    // Keys are lowercase, alphabetically sorted keywords that identify the food.
    // First matching entry wins → add narrower keys before broader ones.
    private static final Map<String, MacrosPer100g> TABLE = Map.ofEntries(
            Map.entry("salmon",        new MacrosPer100g(208, 20.4, 0.0,  13.4, "Rich in EPA+DHA omega-3")),
            Map.entry("tuna",          new MacrosPer100g(132, 28.0, 0.0,   1.2, "Lean protein source")),
            Map.entry("chicken breast",new MacrosPer100g(165, 31.0, 0.0,   3.6, "High protein, low fat")),
            Map.entry("chicken",       new MacrosPer100g(165, 31.0, 0.0,   3.6, "High protein, low fat")),
            Map.entry("egg",           new MacrosPer100g(143, 13.0, 0.7,   9.5, "Complete protein, choline")),
            Map.entry("greek yogurt",  new MacrosPer100g( 59, 10.0, 3.6,   0.4, "Probiotic, high protein")),
            Map.entry("yogurt",        new MacrosPer100g( 59, 10.0, 3.6,   0.4, "Probiotic, high protein")),
            Map.entry("whey protein",  new MacrosPer100g(400, 80.0, 8.0,   4.0, "Fast-absorbing, high leucine")),
            Map.entry("almonds",       new MacrosPer100g(579, 21.2, 21.6, 49.9, "Vitamin E, healthy fats")),
            Map.entry("walnuts",       new MacrosPer100g(654, 15.2, 13.7, 65.2, "ALA omega-3, antioxidants")),
            Map.entry("avocado",       new MacrosPer100g(160,  2.0,  8.5, 14.7, "Monounsaturated fats, potassium")),
            Map.entry("sweet potato",  new MacrosPer100g( 86,  1.6, 20.1,  0.1, "Beta-carotene, complex carbs")),
            Map.entry("brown rice",    new MacrosPer100g(370,  7.9, 77.2,  2.9, "Whole grain, fibre")),
            Map.entry("oats",          new MacrosPer100g(389, 16.9, 66.3,  6.9, "Beta-glucan, slow-release carbs")),
            Map.entry("spinach",       new MacrosPer100g( 23,  2.9,  3.6,  0.4, "Iron, magnesium, folate")),
            Map.entry("broccoli",      new MacrosPer100g( 34,  2.8,  6.6,  0.4, "Sulforaphane, vitamin C")),
            Map.entry("banana",        new MacrosPer100g( 89,  1.1, 22.8,  0.3, "Potassium, quick energy")),
            Map.entry("blueberries",   new MacrosPer100g( 57,  0.7, 14.5,  0.3, "Anthocyanins, low GI")),
            Map.entry("lentils",       new MacrosPer100g(116,  9.0, 20.1,  0.4, "Plant protein, fibre, iron")),
            Map.entry("chickpeas",     new MacrosPer100g(164,  8.9, 27.4,  2.6, "Plant protein, slow carbs")),
            Map.entry("olive oil",     new MacrosPer100g(884,  0.0,  0.0, 100.0,"Monounsaturated, polyphenols")),

            Map.entry("chapati",       new MacrosPer100g(297, 9.0, 55.0, 7.0, "Whole wheat flatbread, staple carb source")),
            Map.entry("lemon rice",    new MacrosPer100g(180, 3.0, 32.0, 4.5, "Rice dish, carb-heavy with oil seasoning")),
            Map.entry("dal",           new MacrosPer100g(116, 9.0, 20.0, 0.4, "Protein-rich lentil preparation")),
            Map.entry("buttermilk",    new MacrosPer100g(40, 3.3, 4.8, 0.9, "Hydrating probiotic dairy drink")),
            Map.entry("amul high protein buttermilk", 
                                          new MacrosPer100g(54, 7.5, 4.0, 0.5, "High protein dairy beverage")),
            Map.entry("muesli",        new MacrosPer100g(420, 24.0, 50.0, 12.0, "High protein cereal mix")),
            Map.entry("pintola high protein muesli with milk (50G museli + 250 ml milk)",
                                          new MacrosPer100g(118, 6.5, 13.33, 4.33, "Protein-rich chocolate cranberry muesli")),
            Map.entry("milk",          new MacrosPer100g(61, 3.2, 4.8, 3.3, "Calcium-rich dairy protein source")),
            Map.entry("cocoa powder",  new MacrosPer100g(228, 19.6, 57.9, 13.7, "Unsweetened chocolate flavoring")),
            Map.entry("coffee powder", new MacrosPer100g(2, 0.1, 0.0, 0.0, "Negligible calories, flavor enhancer"))
    );

    /**
     * Calculate an approximate macro breakdown for a food + quantity pair.
     *
     * @param food     the food name (partial match is fine, e.g. "salmon")
     * @param quantity serving size description (e.g. "100g", "one serving")
     * @return human-readable macro summary string for prompt injection
     */
    public String calculateNutrition(String food, String quantity) {
        if (food == null || food.isBlank()) return "No food specified.";

        String normFood = food.toLowerCase();
        double scaleFactor = parseScaleFactor(quantity);

        // Find matching entry (first key that is a substring of the food query)
        for (Map.Entry<String, MacrosPer100g> entry : TABLE.entrySet()) {
            if (normFood.contains(entry.getKey())) {
                MacrosPer100g m = entry.getValue();
                return formatMacros(food, quantity, scaleFactor, m);
            }
        }

        // Fallback: generic estimate for unknown foods
        return """
                [NutritionCalc: %s / %s]
                Nutrition data not available in local table. A general guideline:
                  • Most whole foods: 50–200 kcal/100 g
                  • Balance protein (~20%%), carbs (~50%%), fats (~30%%) of total calories
                Recommend consulting the USDA FoodData Central database for precise values.
                """.formatted(food, quantity);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private String formatMacros(String food, String quantity, double scale, MacrosPer100g m) {
        return """
                [NutritionCalc: %s / %s]
                  Calories:      %.0f kcal
                  Protein:       %.1f g
                  Carbohydrates: %.1f g
                  Fat:           %.1f g
                  Notes: %s
                (Values are per 100 g scaled by serving size factor: %.2f)
                """.formatted(
                food, quantity,
                m.kcal()     * scale,
                m.proteinG() * scale,
                m.carbG()    * scale,
                m.fatG()     * scale,
                m.notes(),
                scale);
    }

    /**
     * Parse a rough serving-size scale factor from a quantity string.
     * 100 g → 1.0, 200 g → 2.0, "one serving" → 1.0, etc.
     *
     * This is a best-effort heuristic for Phase 3; a real implementation
     * would use a unit-conversion library.
     */
    private double parseScaleFactor(String quantity) {
        if (quantity == null || quantity.isBlank()) return 1.0;
        // Try to extract a leading number
        String trimmed = quantity.trim().replaceAll("[gG].*", "").trim();
        try {
            double grams = Double.parseDouble(trimmed);
            return grams / 100.0;
        } catch (NumberFormatException ignored) {
            // e.g. "one serving", "standard serving" → 1.0
            return 1.0;
        }
    }
}
