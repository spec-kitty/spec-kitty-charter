#!/usr/bin/env bash
# Starts a built charter service, native executable or jar, and checks that it answers (ADR-0046 decision 5).
#
# Usage: scripts/release/smoke-service.sh <artifact> <service-version> <contract-version> [max-start-seconds]
#
#   <artifact>          a native executable, or a Spring Boot jar (run with `java -jar`, Java 25 on PATH or JAVA_HOME)
#   <service-version>   the serviceVersion GET /info must report (gradle.properties `version`)
#   <contract-version>  the contractVersion GET /info must report (info.version of contracts/charter/openapi.yaml)
#   max-start-seconds   how long the first answer may take; default 60
#
# Start 1, with a worktree token, the smoke pack (scripts/release/smoke/config-root) and a guide-fetch allowlist:
#   - GET /api/v1/info answers 200 with both versions, within the limit; the time is printed;
#   - without the token it answers 401 with a problem body (a body written outside a controller);
#   - GET /api/v1/packs/smoke/tactics serves the pack's tactic (the pack is read and validated against its schema);
#   - a directive body missing its required members is a 400 problem, and a complete one without an id is a 422
#     validation_failed (binding, Bean Validation and the domain's refusal);
#   - the allowlist makes the start require the DNS-rebinding guard of the guide fetch: without it the service
#     refuses to start.
# Start 2, under the container profile with every rule met but charter.container.allowed-hosts: the service must
# refuse to start, naming that property (the profile is read at run time, not when the image was built).
#
# Portable bash and curl only, for Linux, macOS and Git Bash on Windows. Exits non-zero, printing the service's own
# output, on the first failed check.
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "usage: $0 <artifact> <service-version> <contract-version> [max-start-seconds]" >&2
  exit 2
fi
artifact="$1"
service_version="$2"
contract_version="$3"
max_start="${4:-60}"

if [[ ! -f "$artifact" ]]; then
  echo "error: no artifact at $artifact" >&2
  exit 1
fi

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
pid=""
log=""

stop() {
  if [[ -n "$pid" ]] && kill -0 "$pid" 2> /dev/null; then
    kill "$pid" 2> /dev/null || true
    wait "$pid" 2> /dev/null || true
  fi
  pid=""
}
cleanup() {
  stop
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  if [[ -n "$log" && -f "$log" ]]; then
    echo "---- service output ($log) ----" >&2
    cat "$log" >&2
    echo "---- end of service output ----" >&2
  fi
  exit 1
}

# A path the service itself can read: on Windows a native executable takes a Windows path.
native_path() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

now_ms() {
  if [[ -n "${EPOCHREALTIME:-}" ]]; then
    local t="${EPOCHREALTIME/,/.}"
    printf '%s' "$(( ${t%.*} * 1000 + 10#$(printf '%.3s' "${t#*.}") ))"
  else
    printf '%s' "$(( $(date +%s) * 1000 ))"
  fi
}

start() { # start <log> <args...>: starts the artifact in the background
  log="$1"
  shift
  case "$artifact" in
    *.jar)
      local java="java"
      if [[ -n "${JAVA_HOME:-}" ]]; then java="$JAVA_HOME/bin/java"; fi
      "$java" -jar "$(native_path "$artifact")" "$@" > "$log" 2>&1 &
      ;;
    *) "$artifact" "$@" > "$log" 2>&1 & ;;
  esac
  pid=$!
}

# curl straight to the loopback service, never through a proxy.
http() { # http <out-file> <curl args...>: prints the status code, 000 when nothing answers
  local code
  code="$(curl --noproxy '*' --silent --max-time 10 --output "$1" --write-out '%{http_code}' "${@:2}")" || true
  printf '%s' "${code:-000}"
}

port=""
for candidate in $(seq 18080 18099); do
  if [[ "$(http /dev/null "http://127.0.0.1:$candidate/")" == "000" ]]; then
    port="$candidate"
    break
  fi
done
[[ -n "$port" ]] || fail "no free port in 18080-18099"
base="http://127.0.0.1:$port/api/v1"

token="$(od -An -tx1 -N20 /dev/urandom | tr -d ' \n')"
[[ ${#token} -eq 40 ]] || fail "could not generate a 40-character token"
export CHARTER_WORKTREE_TOKEN="$token"
# The header comes from a file (curl -H @file), so the token is on no command line.
printf 'Authorization: Bearer %s\n' "$token" > "$work/authorization"
auth="@$(native_path "$work/authorization")"

cp -R "$here/smoke/config-root" "$work/root"
root="$(native_path "$work/root")"

echo "== Start 1: $artifact on port $port"
start "$work/start-1.log" "--server.port=$port" --configRootLocation "$root" \
  --charter.guide-fetch.allowed-hosts=example.org
began="$(now_ms)"
status="000"
while :; do
  status="$(http "$work/info.json" -H "$auth" "$base/info")"
  [[ "$status" == "200" ]] && break
  kill -0 "$pid" 2> /dev/null || fail "the service exited before it answered GET /info"
  if (( $(now_ms) - began > max_start * 1000 )); then
    fail "no 200 from GET /info within ${max_start}s (last status $status)"
  fi
  sleep 0.1
done
elapsed=$(( $(now_ms) - began ))
echo "first answer after ${elapsed} ms (limit ${max_start} s)"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "- \`$(basename "$artifact")\`: first answer to GET /info after ${elapsed} ms" >> "$GITHUB_STEP_SUMMARY"
fi

info="$(cat "$work/info.json")"
[[ "$info" == *"\"serviceVersion\":\"$service_version\""* ]] \
  || fail "GET /info does not report serviceVersion $service_version: $info"
[[ "$info" == *"\"contractVersion\":\"$contract_version\""* ]] \
  || fail "GET /info does not report contractVersion $contract_version: $info"
echo "ok: GET /info reports service $service_version, contract $contract_version"

status="$(http "$work/unauthorized.json" "$base/info")"
[[ "$status" == "401" ]] || fail "GET /info without the token answered $status, not 401"
[[ "$(cat "$work/unauthorized.json")" == *'"status":401'* ]] \
  || fail "the 401 has no problem body: $(cat "$work/unauthorized.json")"
echo "ok: without the token, 401 with a problem body"

status="$(http "$work/tactics.json" -H "$auth" "$base/packs/smoke/tactics")"
[[ "$status" == "200" ]] || fail "GET /packs/smoke/tactics answered $status: $(cat "$work/tactics.json")"
[[ "$(cat "$work/tactics.json")" == *'"id":"smoke-check"'* ]] \
  || fail "the smoke pack's tactic is not served: $(cat "$work/tactics.json")"
echo "ok: the smoke pack is read, validated and served"

status="$(http "$work/incomplete.json" -X POST -H "$auth" -H 'Content-Type: application/json' \
  --data '{"schemaVersion":"1.0"}' "$base/packs/smoke/directives")"
[[ "$status" == "400" && "$(cat "$work/incomplete.json")" == *'"status":400'* ]] \
  || fail "a directive missing its required members answered $status: $(cat "$work/incomplete.json")"
echo "ok: a body missing required members is a 400 problem"

status="$(http "$work/refused.json" -X POST -H "$auth" -H 'Content-Type: application/json' \
  --data '{"schemaVersion":"1.0","title":"Smoke","intent":"Smoke","enforcement":"required"}' \
  "$base/packs/smoke/directives")"
[[ "$status" == "422" && "$(cat "$work/refused.json")" == *'"code":"validation_failed"'* ]] \
  || fail "a directive without an id answered $status: $(cat "$work/refused.json")"
echo "ok: a complete body without an id is a 422 validation_failed"
echo "ok: the service started with a guide-fetch allowlist, so the DNS-rebinding guard is installed"
stop

echo "== Start 2: the container profile without charter.container.allowed-hosts"
mkdir -p "$work/secrets"
SPRING_PROFILES_ACTIVE=container CHARTER_CONTAINER_SECRETS_DIR="$(native_path "$work/secrets")" \
  start "$work/start-2.log" "--server.port=$port" "--configRootLocation=$root"
began="$(now_ms)"
while kill -0 "$pid" 2> /dev/null; do
  if (( $(now_ms) - began > max_start * 1000 )); then
    fail "the service did not refuse the container profile within ${max_start}s"
  fi
  sleep 0.1
done
code=0
wait "$pid" || code=$?
pid=""
(( code != 0 )) || fail "the service exited 0 under the container profile without its allowed hosts"
grep -q "charter.container.allowed-hosts" "$work/start-2.log" \
  || fail "the container profile's refusal does not name charter.container.allowed-hosts"
echo "ok: under the container profile the start is refused, naming charter.container.allowed-hosts"
log=""

echo "smoke: all checks passed for $(basename "$artifact")"
