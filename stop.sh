#!/usr/bin/env bash
set -euo pipefail

CHROMA_CONTAINER="${CHROMA_CONTAINER:-chroma_pdf_search}"

if docker ps --format '{{.Names}}' | grep -q "^${CHROMA_CONTAINER}$"; then
  echo "Stopping ChromaDB container ${CHROMA_CONTAINER}..."
  docker stop "${CHROMA_CONTAINER}" >/dev/null
else
  echo "No running ChromaDB container named ${CHROMA_CONTAINER} found."
fi

echo "Nothing to stop for the app (the indexer runs to completion and exits)."
