#!/usr/bin/env bash
set -euo pipefail

# Configuration (override via env before calling):
CHROMA_IMAGE="${CHROMA_IMAGE:-chromadb/chroma:latest}"
CHROMA_CONTAINER="${CHROMA_CONTAINER:-chroma_pdf_search}"
CHROMA_PORT="${CHROMA_PORT:-8000}"
COLLECTION_NAME="${COLLECTION_NAME:-pdf-search}"

# Start ChromaDB container if not already running
if ! docker ps --format '{{.Names}}' | grep -q "^${CHROMA_CONTAINER}$"; then
  echo "Starting ChromaDB container ${CHROMA_CONTAINER} on port ${CHROMA_PORT}..."
  docker run -d --rm --name "${CHROMA_CONTAINER}" -p "${CHROMA_PORT}:8000" "${CHROMA_IMAGE}"
else
  echo "ChromaDB container ${CHROMA_CONTAINER} already running."
fi

# Wait for ChromaDB to be ready (accept 200/404/etc. as "up" to avoid false negatives)
CHROMA_URL="http://localhost:${CHROMA_PORT}"
echo "Waiting for ChromaDB at ${CHROMA_URL} ..."
ENDPOINTS=("/" "/api/v1/collections" "/api/v1/heartbeat" "/docs")
READY=0
for i in {1..180}; do
  for ep in "${ENDPOINTS[@]}"; do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" "${CHROMA_URL}${ep}" || true)
    if [[ -n "$CODE" && "$CODE" != "000" ]]; then
      echo "ChromaDB responded on ${ep} with HTTP ${CODE}."
      READY=1
      break
    fi
  done
  if [[ $READY -eq 1 ]]; then
    echo "ChromaDB is up."
    break
  fi
  sleep 2
  if [[ "$i" == "180" ]]; then
    echo "ChromaDB did not become ready in time (waited ~6 minutes)." >&2
    echo "Tip: check logs with 'docker logs -f ${CHROMA_CONTAINER}'" >&2
    exit 1
  fi
done

# Run the app to index PDFs
export CHROMA_URL
export COLLECTION_NAME

echo "Running indexer (./gradlew run) against collection ${COLLECTION_NAME} ..."
./gradlew --quiet run --args="index"

echo "Done. You can run searches with: ./gradlew run --args=\"search --q 'your question' [--topK N]\""
