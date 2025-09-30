# Diary Log

Last updated: 2025-09-30 13:31 local

Purpose
- Keep a chronological, developer-facing log of prompts, decisions, errors, rollbacks, and reasoning tied to repository changes.
- Complement SpecDoc (intent/requirements/acceptance) and CodeStyleDoc (conventions). The Diary is narrative and time-ordered.

How We Use This Diary
- Add an entry for every meaningful change, decision, or incident (including build failures and rollbacks).
- Keep entries concise but explicit about what changed and why.
- Reference commits, PRs, or issue slice names when available.
- Prefer local time in the header; include ISO-8601 if cross-timezone clarity is needed.

Entry Template
- Timestamp: <YYYY-MM-DD HH:mm local>
- Context: <prompt/issue/slice>
- Decisions: <key decisions made>
- Changes: <files or areas touched>
- Errors/Rollbacks: <if any>
- Reasoning: <summary of rationale>
- Follow-ups: <next actions>

---

## Entries

- Timestamp: 2025-09-30 13:31 local
  Context: Typo correction — only OPENAI_API_KEY is supported
  Decisions: Removed support for the alias environment variable OPENAOI_API_KEY across code and tests; keep only OPENAI_API_KEY. Updated SpecDoc to reflect this correction.
  Changes: Modified OpenAIEmbeddingService constructor to read only OPENAI_API_KEY; updated OpenAIEmbeddingServiceE2ETest to require OPENAI_API_KEY; updated docs/SpecDoc.md and this Diary.
  Errors/Rollbacks: None; default build expected to remain green; integration test behavior unchanged aside from env var check.
  Reasoning: Aligns with clarified requirement to use only the correct environment variable and avoid ambiguity.
  Follow-ups: None at this time.

- Timestamp: 2025-09-30 13:26 local
  Context: Slice 8 — Validate OpenAI API key via E2E test
  Decisions: Added an integration E2E test that runs only when OPENAI_API_KEY (or alias OPENAOI_API_KEY) is set. The test calls OpenAIEmbeddingService.embed and asserts a non-empty vector. Updated OpenAIEmbeddingService to accept the alias env var. Updated SpecDoc accordingly.
  Changes: Added src/test/java/org/example/search/OpenAIEmbeddingServiceE2ETest.java; modified OpenAIEmbeddingService to read OPENAOI_API_KEY if OPENAI_API_KEY is absent; updated docs/SpecDoc.md and docs/DiaryLogDoc.md header timestamp.
  Errors/Rollbacks: None; default build remains green; integration test runs only when key is present.
  Reasoning: Ensures that when credentials are supplied, the service is actually functional and the key is valid.
  Follow-ups: Consider retries/backoff and rate limit handling for the OpenAI client in a future slice.

- Timestamp: 2025-09-30 12:30 local
  Context: Slice 7 — Add OpenAI embeddings client (optional)
  Decisions: Implemented OpenAIEmbeddingService using Java HttpClient with env-based configuration (OPENAI_API_KEY, OPENAI_EMBED_MODEL, OPENAI_BASE_URL). Updated Main to auto-select OpenAI when key is present; otherwise keep DummyEmbeddingService. Updated README and SpecDoc.
  Changes: Added src/main/java/org/example/search/OpenAIEmbeddingService.java; modified Main.java; updated README.md; updated docs/SpecDoc.md; updated Diary timestamp.
  Errors/Rollbacks: None; build remains green with tests offline.
  Reasoning: Provide a real embedding path without impacting deterministic tests or requiring credentials by default.
  Follow-ups: Consider retries/backoff and model configurability via CLI flags in future slices.

- Timestamp: 2025-09-30 12:22 local
  Context: Slice 6 — Install and use local ChromaDB to index PDFs; Testcontainers for testing
  Decisions: Implemented HttpChromaClient (REST); updated Main to index data/pdfs into local Chroma; added Testcontainers-based integration test excluded by default via JUnit tag; added application plugin.
  Changes: build.gradle (application plugin, Testcontainers, integrationTest task); src/main/java/.../HttpChromaClient.java; updated Main to index PDFs; added ChromaIntegrationTest; updated README and SpecDoc.
  Errors/Rollbacks: Initial build warning about unchecked operations; ensured build passes; integration test tagged to avoid Docker dependency in default build.
  Reasoning: Meet requirement to use local ChromaDB for real indexing while keeping default build deterministic and green; provide optional integration coverage with containers.
  Follow-ups: Consider adding real embedding model and richer Chroma client features (metadata, deletes, retries); add CLI/REST for queries.

- Timestamp: 2025-09-30 12:10 local
  Context: Slice 3 — Add DiaryLogDoc.md and keep all three docs in sync
  Decisions: Introduce docs/DiaryLogDoc.md as a living chronological log; update SpecDoc and CodeStyleDoc to reference and require ongoing maintenance of all three docs.
  Changes: Added DiaryLogDoc.md; plan to update SpecDoc (Slice 2 status Completed; add Slice 3) and CodeStyleDoc (Diary conventions).
  Errors/Rollbacks: None.
  Reasoning: Improves traceability of prompts, decisions, and rationale over time.
  Follow-ups: Ensure build stays green; commit and push to main; continue logging future slices and incidents.

- Timestamp: 2025-09-30 12:13 local
  Context: Slice 4 — Add README.md with overview, requirements, and how to run
  Decisions: Create a root-level README as the primary entry point; keep living docs (SpecDoc, CodeStyleDoc, Diary) synchronized.
  Changes: Added README.md; updated SpecDoc (added Slice 4 and change log); updated CodeStyleDoc (Documentation section and change history).
  Errors/Rollbacks: None.
  Reasoning: Improves discoverability and onboarding; aligns with documentation maintenance policy.
  Follow-ups: Consider adding a CLI/REST entry point in a future slice and expand README accordingly.


- Timestamp: 2025-09-30 12:17 local
  Context: Slice 5 — Create a folder for PDFs to be indexed
  Decisions: Add data/pdfs directory tracked with .gitkeep; add .gitignore to exclude real PDFs; document usage in README; update SpecDoc with Slice 5.
  Changes: Created data/pdfs/.gitkeep; added .gitignore rules; updated README.md; updated docs/SpecDoc.md (Slice 5 and change log).
  Errors/Rollbacks: None.
  Reasoning: Provides a clear, git-safe place to drop PDFs for local experimentation and future indexing pipeline.
  Follow-ups: Build, commit, and push; later slices to add CLI/API to index PDFs from this folder.
