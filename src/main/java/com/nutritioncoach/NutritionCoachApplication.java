package com.nutritioncoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * NutritionCoach — Application Entry Point
 * ══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   This file is the equivalent of your root `server.js` in an Express app,
 *   or the implicit bootstrap that Next.js performs when you run `next start`.
 *   Just as Express needs `app.listen(3000)`, Spring needs `SpringApplication.run()`.
 *
 * @SpringBootApplication is a single shortcut annotation that combines:
 *   1. @Configuration       — this class can define Spring beans (like a config module
 *                             that exports factories).
 *   2. @EnableAutoConfiguration — scans your classpath and auto-wires things;
 *                             similar to how `express-async-errors` or `helmet`
 *                             activate themselves just by being in package.json.
 *   3. @ComponentScan       — finds all @RestController, @Service, @Component, etc.
 *                             in this package tree (like webpack's module resolution).
 *
 * Dependency Injection (DI) — the big mental shift from Node.js:
 *   In Node you write:  const service = new MyService(dep1, dep2)
 *   In Spring you write: declare the dep in a constructor, Spring builds the graph.
 *   This is Spring's IoC (Inversion of Control) container — it's like having
 *   a factory that resolves the entire `import` tree for you automatically.
 *
 * Book ref: Chapter 1 — Introduction to AI Engineering
 *   (project bootstrap; the foundation everything else is built on)
 */
@SpringBootApplication
public class NutritionCoachApplication {

    public static void main(String[] args) {
        // MERN analogy: equivalent to `app.listen(8080, () => console.log('ready'))`
        // Spring reads application.yml, assembles all beans, starts the embedded Tomcat.
        SpringApplication.run(NutritionCoachApplication.class, args);
    }
}
