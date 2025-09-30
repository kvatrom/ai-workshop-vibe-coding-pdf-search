# Spec Doc

This document is the living specification for the PDF Semantic Search with ChromaDB project. It captures intent, requirements, and acceptance criteria for each incremental slice. It should be updated alongside code changes.

Last updated: 2025-09-30 (Removed OPENAOI_API_KEY alias; only OPENAI_API_KEY supported)

## Purpose and Intent
- Provide a minimal, testable foundation for ingesting PDFs, generating embeddings, upserting to ChromaDB, and performing semantic search.
- Iterate in small slices with clear acceptance criteria to ensure continuous progress and shared understanding.

## Glossary
- Chunk: A logical segment of text extracted from a PDF (currently one chunk per page).
- Embedding: Numerical vector representation of a text chunk or query.
- Slice: A small, incremental deliverable with explicit acceptance criteria.

## Architecture Overview (current stub)
- PdfTextExtractor: Functional interface to extract text chunks from PDFs.
- PdfBoxTextExtractor: PDFBox-based implementation (chunk per page).
- EmbeddingService: Functional interface to produce vector embeddings.
- DummyEmbeddingService: Deterministic fake embedder for tests.
- ChromaClient: Minimal interface to interact with a ChromaDB-like store.
- PdfSearchService: Orchestrates extract → embed → upsert and query.

## Slices

### Slice 1: Stub project bootstrapping
Intent:
- Establish a Java 21 project with Checkstyle, tests, and a basic semantic search pipeline stub.

Functional Requirements:
- Extract text from PDF(s) into chunks.
- Generate deterministic embeddings for chunks and queries.
- Provide an interface for Chroma upsert and query (no real network calls yet).
- Orchestrate indexing and querying through a service API.

Non‑Functional Requirements:
- Java 21 toolchain.
- Checkstyle with local config and default IntelliJ formatting conventions.
- JUnit 5 tests.
- Main branch named `main`.

Acceptance Criteria:
- `./gradlew clean build` succeeds on Java 21.
- Tests cover PDF extraction and orchestrated flow without external services.
- Checkstyle passes for main and test sources.
- Repository pushed to remote on branch `main`.

Status: Completed
Notes:
- Text extraction uses one chunk per page.
- Dummy embeddings ensure deterministic tests without network dependency.

### Slice 2: Spec Doc artifact (this change)
Intent:
- Introduce a living specification document that will evolve with the codebase.

Functional Requirements:
- Add `docs/SpecDoc.md` with structure for intent, requirements, and acceptance criteria per slice.
- Establish a changelog section to record future slices and updates.

Acceptance Criteria:
- Spec Doc exists under `docs/SpecDoc.md` and describes completed Slice 1 and the current slice.
- Build remains green (no code impact).

Status: Completed

### Slice 3: Diary Log artifact (this change)
Intent:
- Introduce a chronological engineering diary to log prompts, decisions, errors, rollbacks, and reasoning.

Functional Requirements:
- Add `docs/DiaryLogDoc.md` as a living log with a clear entry template.
- Keep the Diary updated for each meaningful change and incident.
- Cross-link with SpecDoc and CodeStyleDoc where relevant.

Acceptance Criteria:
- DiaryLogDoc.md exists with initial entries and guidance.
- SpecDoc and CodeStyleDoc updated to reference the Diary and maintenance expectations.
- Build remains green.

Status: Completed

### Slice 4: README artifact (this change)
Intent:
- Provide a clear entry point (README.md) explaining project purpose, requirements, and how to build/run/tests.

Functional Requirements:
- Add README.md at the repository root with overview, exercise requirements, quick start, and pointers to living docs.

Acceptance Criteria:
- README.md exists and accurately reflects the current stub; build remains green.

Status: Completed

### Slice 5: PDF input folder (this change)
Intent:
- Provide a conventional, git-safe place in the repository to drop PDF documents for local experimentation and future indexing flows.

Functional Requirements:
- Create data/pdfs directory in the repository.
- Ensure the folder is tracked with a placeholder file (e.g., .gitkeep).
- Add .gitignore rules to ignore actual PDFs under data/pdfs while keeping the folder tracked.
- Document the folder usage in README.

Acceptance Criteria:
- data/pdfs exists in the repo with a .gitkeep.
- .gitignore prevents committing PDF files from data/pdfs by default.
- README documents where to place PDFs.
- Build remains green.

Status: Completed

### Slice 6: Local ChromaDB integration + indexing data/pdfs (this change)
Intent:
- Use a local ChromaDB (Docker) instance to index PDFs from data/pdfs and enable real upsert/query flows.

Functional Requirements:
- Implement a ChromaClient over HTTP to talk to Chroma REST API.
- Update the application entry point to index all PDFs in data/pdfs into a configured Chroma collection.
- Provide Testcontainers-based integration test that starts chromadb/chroma and verifies indexing + query.

Non‑Functional Requirements:
- Keep default unit tests deterministic and offline; run container-based tests under a separate Gradle task.
- Maintain Java 21 toolchain and Checkstyle compliance.

Acceptance Criteria:
- ./gradlew clean build is green (integration test excluded by default via tag).
- ./gradlew integrationTest runs and passes when Docker is available.
- Running docker run --rm -p 8000:8000 chromadb/chroma:latest and ./gradlew run indexes PDFs from data/pdfs without errors.

Status: Completed

## Future Slices (Proposed)
- Enhance Chroma client (robust error handling, retries, metadata, delete/upsert semantics).
- Configurable chunking strategies (by characters, tokens, or semantic boundaries).
- Real embedding provider integration (e.g., OpenAI or local model) with retry/backoff.
- Query ranking refinement and evaluation harness.
- CLI or minimal REST API to index and query.

### Slice 7: OpenAI embeddings client (this change)
Intent:
- Provide an optional real embedding provider using OpenAI’s embeddings API while keeping default tests offline/deterministic.

Functional Requirements:
- Implement OpenAIEmbeddingService implementing EmbeddingService using Java HttpClient.
- Configure via environment variables: OPENAI_API_KEY (required to enable), OPENAI_EMBED_MODEL (default text-embedding-3-small), OPENAI_BASE_URL (default https://api.openai.com).
- Update Main to auto-select OpenAIEmbeddingService when OPENAI_API_KEY is present; otherwise fall back to DummyEmbeddingService.

Acceptance Criteria:
- ./gradlew clean build remains green without any OpenAI key (tests offline, deterministic).
- README documents how to provide OPENAI_API_KEY and optional overrides.
- Running with OPENAI_API_KEY set uses OpenAI embeddings at runtime.

Status: Completed

### Slice 8: OpenAI API key validation via E2E test (this change)
Intent:
- Validate that when an OpenAI API key is provided, the OpenAIEmbeddingService can successfully call the embeddings endpoint.

Functional Requirements:
- Add an integration test that is executed only when an API key is present in the environment.
- The test must call OpenAIEmbeddingService.embed on a sample string and assert a non-empty vector is returned.
- Keep default unit tests offline; only run this test under the existing integrationTest task.
- Only support OPENAI_API_KEY; alias environment variables are not supported.

Acceptance Criteria:
- ./gradlew clean build remains green without an API key (test skipped and excluded by default due to tag).
- ./gradlew integrationTest passes when a valid OPENAI_API_KEY is present and fails with an invalid key (surface 401 from API).
- SpecDoc and Diary updated to reflect this slice.

Status: Completed

### Slice 9: Doc2Query (synthetic query expansion) during indexing (this change)
Intent:
- Generate N synthetic user questions per semantic chunk to improve recall and QA-style queries.

Functional Requirements:
- Add Doc2QueryGenerator with OpenAI-backed implementation (chat completions) and an offline Simple fallback.
- Extend PdfSearchService to upsert chunk + question embeddings; link questions to parent chunk via metadata (parentChunkId).
- CLI flags: --doc2query-count, --no-doc2query; env DOC2QUERY_COUNT (default 3); support OPENAI_DOC2QUERY_MODEL.

Acceptance Criteria:
- Default build remains green (offline fallback used when no OPENAI_API_KEY).
- Index mode generates and upserts questions; metadata includes filename/page/type.
- Unit test verifies question upserts and linkage to chunk.

Status: Completed

### Slice 10: Idempotent indexing (stable IDs) (this change)
Intent:
- Avoid duplicate entries upon re-indexing the same document by using stable, deterministic IDs.

Functional Requirements:
- Derive chunk IDs from SHA-256 over filename + page + text; derive question IDs from chunkId + hash(question).
- Preserve existing metadata; maintain parentChunkId linkage.

Acceptance Criteria:
- Re-running index over same inputs results in updates/replacements instead of new random IDs.
- Build and tests remain green.

Status: Completed

### Slice 11: Chroma 0.6.3 compatibility and tests (this change)
Intent:
- Ensure the client and tests are compatible with Chroma 0.6.3.

Functional Requirements:
- Tolerate boolean 201 response on /api/v1/collections/{id}/add.
- Force HTTP/1.1 on HttpClient to avoid h2c upgrade issues with some images.
- Resolve existing collection IDs via POST /api/v1/collections/get to handle 409 conflicts reliably (some 0.6.3 builds return 400 on GET /api/v1/collections).
- Pin Testcontainers integration test image to chromadb/chroma:0.6.3.
- Allow multiple search runs without recreating collections; when collection exists but cannot be resolved via API on some builds, use a local on-disk cache of collection IDs.

Acceptance Criteria:
- Local start.sh uses 0.6.3; indexing and search run without exceptions, even if the collection already exists.
- Multiple consecutive searches do not fail with 409/405/400; at worst, they return empty results if the collection cannot be resolved.
- Integration tests pass when Docker is available.

Status: Completed

## Change Log
- 2025-09-30: Added Spec Doc artifact and documented Slice 1 and Slice 2 structure.
- 2025-09-30: Added Diary Log artifact; updated SpecDoc and CodeStyleDoc to reference maintenance of all three docs.
- 2025-09-30: Added README.md with overview, requirements, and how to run; documented as Slice 4.
- 2025-09-30: Added data/pdfs input folder, .gitignore, and README docs; documented as Slice 5.
- 2025-09-30: Local ChromaDB integration with HttpChromaClient, Testcontainers integration test, updated README, and runtime indexer in Main; documented as Slice 6.
- 2025-09-30: Added OpenAIEmbeddingService and runtime selection via env var; updated README; documented as Slice 7.
- 2025-09-30: Added E2E integration test for OpenAIEmbeddingService; documented as Slice 8.
- 2025-09-30: Implemented doc2query (synthetic question expansion) with OpenAI and offline fallback; documented as Slice 9.
- 2025-09-30: Implemented idempotent indexing via stable IDs; documented as Slice 10.
- 2025-09-30: Ensured Chroma 0.6.3 compatibility and pinned Testcontainers image; documented as Slice 11.
