#!/usr/bin/env bash
# Hello-world check 4: one vision call over one record's photos (already resized JPEGs).
# Usage: poc/vision.sh <dir-with-jpgs>   Model: VISION_MODEL env var (config setting).
set -euo pipefail
dir="${1:?dir}"; model="${VISION_MODEL:-claude-sonnet-5-5}"
prompt='These are all the photos of ONE used vinyl record, in arbitrary order. For each photo (photo_1, photo_2, ... in the order given) return its role: front, back, labels, barcode, runout_a, runout_b, other, or unclear. Then list structured observations ONLY for what is visible: catalog_number, label, country, barcode_digits, format, matrix_text, artist, title, year. Every observation needs the photo it came from and the raw text exactly as printed. If something is not visible, omit it; never guess. Do not grade condition. Reply with JSON only: {"photos":[{"id":"photo_1","role":"...","note":"..."}],"observations":[{"field":"...","value":"...","raw_text":"...","photo":"photo_1"}]}'
parts="$dir/parts.jsonl"; : > "$parts"; n=0
for f in "$dir"/*.jpg; do n=$((n+1))
  jq -nc --arg n "photo_$n" --rawfile d <(base64 -i "$f" | tr -d '\n') '{type:"text",text:$n},{type:"image",source:{type:"base64",media_type:"image/jpeg",data:$d}}' >> "$parts"
done
jq -s --arg model "$model" --arg prompt "$prompt" '{model:$model,max_tokens:8000,messages:[{role:"user",content:(. + [{type:"text",text:$prompt}])}]}' "$parts" > "$dir/request.json"
curl -sS https://api.anthropic.com/v1/messages -H "x-api-key: $ANTHROPIC_API_KEY" -H "anthropic-version: 2023-06-01" -H "content-type: application/json" -d @"$dir/request.json" > "$dir/response.json"
jq '{type,error,model,usage,text:(.content[0].text // null)}' "$dir/response.json"
