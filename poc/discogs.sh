#!/usr/bin/env bash
# Hello-world checks 1-3: Discogs search, release, marketplace stats/price suggestions, image URLs.
# Usage: poc/discogs.sh <path-and-query>   e.g. "/database/search?barcode=..."  Prints status, rate headers, body.
set -euo pipefail
curl -sS -D out/headers.txt -o out/body.json -w 'HTTP %{http_code}\n' \
  -H "Authorization: Discogs token=$DISCOGS_TOKEN" -H "User-Agent: vinyl-assistant-poc/0.1" \
  "https://api.discogs.com$1"
grep -i '^x-discogs-ratelimit' out/headers.txt || true
