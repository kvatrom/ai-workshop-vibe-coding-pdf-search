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

3) Run the app
- ./gradlew run
The Main class currently prints a stub message. The most illustrative behavior is in tests.

How it works (current stub)
- PdfTextExtractor (interface): extracts text chunks from a PDF InputStream.
- PdfBoxTextExtractor (impl): uses Apache PDFBox; returns one chunk per page.
- EmbeddingService (interface): converts text to a vector embedding.
- DummyEmbeddingService (impl): deterministic pseudo-embeddings for tests; no network calls.
- ChromaClient (interface): minimal Vector DB contract with upsert/query.
- PdfSearchService: orchestrates extract → embed → upsert and query.

Try it locally
- The tests generate small PDFs on the fly and validate extraction and the indexing/search flow with an in-memory fake Chroma client.
  See: src/test/java/org/example/search/PdfBoxTextExtractorTest.java
       src/test/java/org/example/search/PdfSearchServiceTest.java

Project structure
- src/main/java/...    Core classes and interfaces
- src/test/java/...    JUnit 5 tests
- config/checkstyle/   Checkstyle configuration
- docs/                Living documentation (SpecDoc, CodeStyleDoc, DiaryLog)
- build.gradle         Java 21 toolchain, dependencies, Checkstyle and JUnit config

Common tasks
- Lint (Checkstyle reports): after a build, check reports in build/reports/checkstyle
- Update docs: keep SpecDoc, CodeStyleDoc, DiaryLog up to date with each change

Extending this stub
- Replace DummyEmbeddingService with a real embedding provider
- Implement a real ChromaDB client (HTTP/gRPC)
- Add a CLI or minimal REST API to index/query PDFs
- Support alternate chunking strategies (by characters/tokens/semantic)

FAQ
- Why do tests not call any external service? To ensure deterministic behavior and fast local development.
- Can I index a real PDF? Yes—instantiate PdfSearchService with PdfBoxTextExtractor and a ChromaClient implementation; feed your PDF InputStream to indexPdf().

See also
- docs/SpecDoc.md — living specification (slices, requirements, acceptance criteria)
- docs/CodeStyleDoc.md — conventions and patterns
- docs/DiaryLogDoc.md — chronological log
