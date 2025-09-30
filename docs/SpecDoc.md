# Spec Doc

This document is the living specification for the PDF Semantic Search with ChromaDB project. It captures intent, requirements, and acceptance criteria for each incremental slice. It should be updated alongside code changes.

Last updated: 2025-09-30 (updated for README)

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

## Future Slices (Proposed)
- Real ChromaDB client integration (HTTP/gRPC) with configurable collection.
- Configurable chunking strategies (by characters, tokens, or semantic boundaries).
- Real embedding provider integration (e.g., OpenAI or local model) with retry/backoff.
- Persistence and idempotent indexing (document IDs, page references, metadata).
- Query ranking refinement and evaluation harness.
- CLI or minimal REST API to index and query.

## Change Log
- 2025-09-30: Added Spec Doc artifact and documented Slice 1 and Slice 2 structure.
- 2025-09-30: Added Diary Log artifact; updated SpecDoc and CodeStyleDoc to reference maintenance of all three docs.
- 2025-09-30: Added README.md with overview, requirements, and how to run; documented as Slice 4.
