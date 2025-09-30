# CodeStyleDoc — Conventions and Patterns

Last updated: 2025-09-30 16:03 local

Purpose
- This living document records our conventions, patterns, and agreed rules to ensure consistent authoring and review across the repository.
- It complements automated checks (Checkstyle) and IDE formatting (IntelliJ defaults) and explains the intent behind them.

Scope
- Applies to all Java code, tests, Gradle build scripts, and markdown docs in this repository.
- If a rule here conflicts with automated tooling, tooling wins and this doc should be updated.

Tooling Baseline
- Java: 21 (Gradle toolchain configured).
- Formatter: IntelliJ IDEA default formatter settings. Do not commit custom .editorconfig unless agreed here.
- Lint: Checkstyle (config/checkstyle/checkstyle.xml) runs in the build (check task). Fix violations before commit.
- Tests: JUnit 5; tests must pass locally before pushing.

General Principles
- Prefer clarity over cleverness. Small, composable, predictable units.
- Fail fast: validate inputs and throw informative unchecked exceptions for programmer errors.
- Be deterministic in tests and examples. Avoid hidden global state.
- Immutability by default; minimize mutability and side effects.

Naming and Structure (Java)
- Packages: lower case, dot-separated (e.g., org.example.search).
- Classes: PascalCase; interfaces named by capability (e.g., PdfTextExtractor).
- Methods and fields: camelCase. Constants: UPPER_SNAKE_CASE.
- Keep classes small and focused; prefer final classes unless a type is designed for extension.
- Keep methods short; extract helper methods for readability.

Java 21 Usage
- Use records for simple immutable data carriers (e.g., ChromaClient.SearchResult).
- Use FunctionalInterface where a single abstract method is intended.
- Use var sparingly for obvious types; do not reduce readability.
- Prefer java.util.random RandomGenerator over legacy Random when determinism or algorithms matter (already used in DummyEmbeddingService).

Functional Style Guidelines
- Prefer pure functions and referential transparency where practical.
- Use streams for collection transformations when they improve readability; otherwise, use enhanced for loops.
- Avoid deep stream pipelines; extract intermediate variables or methods.
- Avoid side effects in stream operations; collect results explicitly.

Nullability and Validation
- Validate public method parameters with Objects.requireNonNull or documented defaults.
- Use Objects.requireNonNullElse for sensible defaults (see PdfSearchService collection name).
- Avoid returning null; return empty collections or Optional when appropriate (we largely avoid Optional in fields and setters; use in local return types if it helps clarity).

Errors and Exceptions
- Wrap checked exceptions at boundaries with meaningful messages (IllegalStateException when IO fails in extraction, as implemented).
- Do not swallow exceptions silently; if an error is non-fatal, log and continue with a documented fallback.

Logging
- For this stub, we use System.out only in Main; avoid println in library code. If/when logging is introduced, prefer slf4j facade and keep logs structured and minimal.

Secrets and Configuration
- Do not commit secrets. Provide credentials via environment variables (e.g., OPENAI_API_KEY). Document required env vars in README.
- Keep defaults safe; code should operate in a deterministic, offline mode when secrets are absent (e.g., fall back to DummyEmbeddingService).

Collections and Streams
- Do not expose internal mutable collections. Prefer unmodifiable views or defensive copies if necessary.
- Use List.toList() (Java 16+) or Collectors.toUnmodifiableList() when immutability is desired.

Testing
- Name tests to describe behavior. Favor given/when/then arrangement in code blocks or comments.
- Keep tests independent and deterministic; avoid network and time dependencies.
- Use helper methods to construct test data (see PdfSearchServiceTest.twoPagePdf()).

Gradle and Dependencies
- Lock Java toolchain in build.gradle (already configured).
- Keep dependencies minimal. Prefer platform BOMs for test libs as done for JUnit.
- Avoid dynamic versions. Bump versions deliberately via PR and update this doc if rules change.

Documentation
- Keep README.md accurate as the primary entry point (overview, requirements, how to build/run/tests). Update when project behavior or requirements change.
- Keep docs/SpecDoc.md up-to-date for slices (intent/requirements/acceptance). Use this CodeStyleDoc.md for conventions.
- Maintain docs/DiaryLogDoc.md as a chronological log of prompts, decisions, errors, rollbacks, and reasoning. Add an entry for each meaningful change or incident.
- When codifying a new convention, add an entry here in a short, actionable form and reference any supporting tooling change.

Process Discipline (Always)
- After each meaningful step/change:
  - Update all three living docs: SpecDoc.md (slices/acceptance), CodeStyleDoc.md (conventions/process), DiaryLogDoc.md (entry).
  - Run ./gradlew clean build (this runs Checkstyle and tests). Fix issues until green.
  - Commit with a descriptive message and push to origin main.

Repository Layout
- data/pdfs: Local PDF drop folder for indexing. Keep .gitkeep; actual PDFs are ignored by git by default.

Review Checklist (Quick)
- [ ] Names and intent are clear.
- [ ] Methods/classes are small and focused; immutability preferred.
- [ ] Input validation present; exceptions informative.
- [ ] Formatting matches IntelliJ defaults; Checkstyle passes.
- [ ] Tests are deterministic and cover the change.
- [ ] No unnecessary dependencies introduced.

Change History
- 2025-09-30: Initial version created; aligned with current codebase (PDFBox extractor, functional interfaces, dummy embeddings, Checkstyle).
- 2025-09-30 12:13: Added README maintenance note under Documentation; timestamp updated.
- 2025-09-30 12:17: Documented repository layout for data/pdfs; timestamp updated.
- 2025-09-30 16:03: Added Process Discipline section (update 3 docs, run clean build, commit/push after each step); timestamp updated.
