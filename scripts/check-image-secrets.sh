#!/usr/bin/env bash
# Proves a built image contains no .env file and no Anthropic API key (ADR-034).
# Prints counts only, never the key. Exits 0 on PASS, 1 on FAIL (a match, or a control that found nothing).
#
#   scripts/check-image-secrets.sh [image] [env-file]
#   defaults: franchiseads:local and ./.env (the key in it is searched for; without one, only the patterns are)
#
# The exported filesystem is streamed, not written to disk; only /app (the jars, ~60 MB) is extracted.
# Every docker export is checked for success, and positive controls show each search can find a known string.
# Needs bash, docker, tar, grep and unzip (Linux, macOS, or Git Bash on Windows).
set -euo pipefail

IMAGE=${1:-franchiseads:local}
ENV_FILE=${2:-.env}
WORK=$(mktemp -d)

KEY=""
if [ -f "$ENV_FILE" ]; then
  KEY=$(grep -E '^ANTHROPIC_API_KEY=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)
fi
if [ -n "$KEY" ]; then
  echo "key from $ENV_FILE: ${#KEY} characters (value not printed)"
else
  echo "no ANTHROPIC_API_KEY in $ENV_FILE: only the pattern searches run"
fi

CID=$(docker create "$IMAGE")
trap 'docker rm "$CID" >/dev/null; rm -rf "$WORK"' EXIT
FAILED=0

# Matching lines for a fixed string in the exported filesystem stream. Exits if docker export fails.
stream_count() {
  local out count export_status grep_status
  out=$(docker export "$CID" | grep -a -c -F -- "$1"; echo "${PIPESTATUS[*]}")
  read -r count export_status grep_status <<<"$(echo "$out" | tr '\n' ' ')"
  [ "$export_status" = 0 ] || { echo "docker export failed ($export_status)" >&2; exit 1; }
  [ "$grep_status" = 0 ] || [ "$grep_status" = 1 ] || { echo "grep failed ($grep_status)" >&2; exit 1; }
  echo "$count"
}

# Matching lines for a fixed string across every jar under /app, read with unzip -p (nothing extracted).
jar_count() {
  local total=0 jar
  for jar in "$WORK"/app/*.jar "$WORK"/app/lib/*.jar; do
    total=$((total + $(unzip -p "$jar" | grep -a -c -F -- "$1" || true)))
  done
  echo "$total"
}

# expect <label> <count> <zero|nonzero>
expect() {
  local verdict=ok
  if { [ "$3" = zero ] && [ "$2" != 0 ]; } || { [ "$3" = nonzero ] && [ "$2" = 0 ]; }; then
    verdict=FAIL; FAILED=1
  fi
  printf '  %-46s %6s  %s\n' "$1" "$2" "$verdict"
}

echo "image: $IMAGE"
docker export "$CID" | tar -t > "$WORK/entries.txt"
echo "filesystem entries: $(wc -l < "$WORK/entries.txt")"

echo "1. files named .env or .env.* in the image filesystem"
expect ".env files" "$(grep -c -E '(^|/)\.env(\.|$)' "$WORK/entries.txt" || true)" zero
expect "control: entries under app/lib/" "$(grep -c '^app/lib/.' "$WORK/entries.txt" || true)" nonzero

echo "2. raw filesystem content"
if [ -n "$KEY" ]; then expect "the key from $ENV_FILE" "$(stream_count "$KEY")" zero; fi
expect "'ANTHROPIC_API_KEY='" "$(stream_count 'ANTHROPIC_API_KEY=')" zero
expect "'sk-ant-'" "$(stream_count 'sk-ant-')" zero
expect "control: 'VERSION_CODENAME='" "$(stream_count 'VERSION_CODENAME=')" nonzero

echo "3. inside every jar under /app"
docker export "$CID" | tar -x -C "$WORK" app
echo "  jars: $(ls "$WORK"/app/*.jar "$WORK"/app/lib/*.jar | wc -l)"
expect "entries named .env*" "$(for j in "$WORK"/app/*.jar "$WORK"/app/lib/*.jar; do
  unzip -Z1 "$j"; done | grep -c -E '(^|/)\.env(\.|$)' || true)" zero
if [ -n "$KEY" ]; then expect "the key from $ENV_FILE" "$(jar_count "$KEY")" zero; fi
expect "'ANTHROPIC_API_KEY='" "$(jar_count 'ANTHROPIC_API_KEY=')" zero
expect "'sk-ant-'" "$(jar_count 'sk-ant-')" zero
expect "control: '\${ANTHROPIC_API_KEY:}' placeholder" "$(jar_count '${ANTHROPIC_API_KEY:}')" nonzero

echo "4. image config and build history"
expect "Env entries naming ANTHROPIC" \
  "$(docker image inspect "$IMAGE" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -c ANTHROPIC || true)" zero
if [ -n "$KEY" ]; then
  expect "history lines with the key" "$(docker history --no-trunc "$IMAGE" | grep -c -F -- "$KEY" || true)" zero
fi
expect "history lines naming ANTHROPIC" "$(docker history --no-trunc "$IMAGE" | grep -c ANTHROPIC || true)" zero

if [ "$FAILED" = 0 ]; then echo "PASS"; else echo "FAIL"; exit 1; fi
