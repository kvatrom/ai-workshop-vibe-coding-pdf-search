# PDF Semantic Search with ChromaDB — Stub Project

Last updated: 2025-09-30

Overview
- This repository is a minimal, testable Java 21 stub for experimenting with PDF semantic search using a ChromaDB-like interface.
- It extracts text from PDFs (one chunk per page), generates deterministic dummy embeddings, and defines a minimal client interface for upserting/querying a vector store.
- The goal is to provide a clean foundation to iterate in small slices. See docs/SpecDoc.md for the living specification.

Requirements for this exercise
- Java 21 (the Gradle toolchain ensures builds run against Java 21)
- Gradle Wrapper (use ./gradlew)
- No external services required: tests run locally and deterministically
- Checkstyle enforced in the build
- Default branch: main
- Living docs maintained in docs/: SpecDoc.md, CodeStyleDoc.md, DiaryLogDoc.md

Quick start
1) Build and run checks
- ./gradlew clean build
This compiles the project, runs Checkstyle, and executes tests.

2) Run tests only
- ./gradlew test

3) Run the app (indexes PDFs in data/pdfs into local ChromaDB)
- Start ChromaDB locally (Docker):
  docker run --rm -p 8000:8000 chromadb/chroma:0.6.3
- Place PDFs under data/pdfs/
- Run the indexer:
  ./gradlew run --args="index [--dir data/pdfs] [--doc2query-count N] [--no-doc2query]"
Environment variables:
- CHROMA_URL (default http://localhost:8000)
- COLLECTION_NAME (default pdf-search)
- OPENAI_API_KEY (optional; when set, uses OpenAI embeddings instead of dummy and enables OpenAI doc2query generator)
- OPENAI_EMBED_MODEL (optional; default text-embedding-3-small)
- OPENAI_BASE_URL (optional; default https://api.openai.com)
- OPENAI_DOC2QUERY_MODEL (optional; default gpt-4o-mini)
- DOC2QUERY_COUNT (optional; default 3)

How it works (current)
- PdfTextExtractor (interface): extracts text chunks from a PDF InputStream.
- PdfBoxTextExtractor (impl): uses Apache PDFBox; returns one chunk per page.
- SemanticChunker: heuristic sentence-aware chunking with target sizes.
- EmbeddingService (interface): converts text to a vector embedding.
- DummyEmbeddingService (impl): deterministic pseudo-embeddings for tests; no network calls.
- OpenAIEmbeddingService (impl): real embeddings via OpenAI if OPENAI_API_KEY is set.
- Doc2QueryGenerator (interface): generates synthetic search questions per chunk.
- OpenAIDoc2QueryGenerator (impl): uses OpenAI chat completions; offline fallback SimpleDoc2QueryGenerator.
- ChromaClient (interface): minimal Vector DB contract with upsert/query.
- HttpChromaClient (impl): HTTP v1 client for Chroma 0.6.x with robust response handling.
- PdfSearchService: orchestrates extract → (doc2query) → embed → upsert and query.

Try it locally
- The tests generate small PDFs on the fly and validate extraction and the indexing/search flow with an in-memory fake Chroma client.
  See: src/test/java/org/example/search/PdfBoxTextExtractorTest.java
       src/test/java/org/example/search/PdfSearchServiceTest.java
       src/test/java/org/example/search/PdfSearchServiceDoc2QueryTest.java

Integration tests with Testcontainers (optional)
- Requires Docker available locally.
- Run integration tests (spins up chromadb/chroma:latest):
  ./gradlew integrationTest

Project structure
- src/main/java/...    Core classes and interfaces
- src/test/java/...    JUnit 5 tests
- config/checkstyle/   Checkstyle configuration
- docs/                Living documentation (SpecDoc, CodeStyleDoc, DiaryLog)
- build.gradle         Java 21 toolchain, dependencies, Checkstyle and JUnit config

Common tasks
- Lint (Checkstyle reports): after a build, check reports in build/reports/checkstyle
- Update docs: keep SpecDoc, CodeStyleDoc, DiaryLog up to date with each change

Using OpenAI embeddings (optional)
- Set your API key as an environment variable before running:
  export OPENAI_API_KEY="sk-..."
- Optional overrides:
  export OPENAI_EMBED_MODEL="text-embedding-3-small"   # default
  export OPENAI_BASE_URL="https://api.openai.com"      # default; set to your gateway if needed
- Then run the app as usual (it will automatically use OpenAI embeddings):
  ./gradlew run
- Note: Do not commit your API key. Store it in your shell profile or a secure secret manager.

Extending this stub
- Replace DummyEmbeddingService with a real embedding provider
- Enhance the ChromaDB client (error handling, retries, metadata)
- Add a CLI or minimal REST API to index/query PDFs
- Support alternate chunking strategies (by characters/tokens/semantic)

FAQ
- Why do tests not call any external service? To ensure deterministic behavior and fast local development.
- Can I index a real PDF? Yes—instantiate PdfSearchService with PdfBoxTextExtractor and a ChromaClient implementation; feed your PDF InputStream to indexPdf().

See also
- docs/SpecDoc.md — living specification (slices, requirements, acceptance criteria)
- docs/CodeStyleDoc.md — conventions and patterns
- docs/DiaryLogDoc.md — chronological log


## Adding PDFs for indexing
- Place your PDF files under data/pdfs/.
- This folder is tracked via a .gitkeep file, while actual PDFs are ignored by git to avoid committing large or private documents.
- Future slices will add a CLI or minimal API to index these PDFs into a ChromaDB collection.
