#!/usr/bin/env bash
# Service usability run (FR-011, SC-002, NFR-003): a client that starts the charter service, loads the open-packs,
# activates this repository's charter and reads an agent profile by following links only.
#
#   scripts/usability/service-usability-run.sh --open-packs ../spec-kitty-open-packs [--profile java-jenny]
#       [--jar <path>] [--keep] [--packs internal,qa] [--strict] [--attach <base-url>]
#
# It exits 0 only if every hop of the walk answered 2xx and the profile reads were fast enough. The exit codes are
# in scripts/usability/README.md. The last line of the output is a one-line summary.

set -u -o pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)

OPEN_PACKS=""
PROFILE="java-jenny"
JAR=""
KEEP=0
STRICT=0
ATTACH=""
EXTRA_PACKS="internal,qa"
READS=20
P95_LIMIT_MS=500

usage() {
    sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-2}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --open-packs) OPEN_PACKS=${2:-}; shift 2 ;;
        --profile) PROFILE=${2:-}; shift 2 ;;
        --jar) JAR=${2:-}; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --strict) STRICT=1; shift ;;
        --attach) ATTACH=${2:-}; shift 2 ;;
        --packs) EXTRA_PACKS=${2:-}; shift 2 ;;
        -h|--help) usage 0 ;;
        *) echo "unknown option: $1" >&2; usage 2 ;;
    esac
done

# ---------------------------------------------------------------------------------------------------------------
# State: hops, workarounds, timings

HOP_OK=0
HOP_FAIL=0
HOPS_FAILED=()
declare -A MISSING=()
WORKAROUNDS=()
DROPPED=()
P95_PACK="NA"
P95_ACT="NA"
P95_RESULT="NA"
SERVER_PID=""
WORK=""
BASE=""
STATUS=""
TIME=""
BODY=""
HREF=""
SEQ=0
EXIT_CODE=0

say() { printf '%s\n' "$*"; }
die() { local code=$1; shift; printf 'error: %s\n' "$*" >&2; EXIT_CODE=$code; exit "$code"; }

hop_ok() { HOP_OK=$((HOP_OK + 1)); printf '  [ ok ] %-4s %s\n' "$1" "$2"; }
hop_fail() { HOP_FAIL=$((HOP_FAIL + 1)); HOPS_FAILED+=("$1: $2"); printf '  [FAIL] %-4s %s\n' "$1" "$2"; }
workaround() { WORKAROUNDS+=("$1"); printf '  [WORKAROUND] %s\n' "$1"; }

cleanup() {
    local status=$?
    if [[ -n $SERVER_PID ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        if [[ $KEEP -eq 1 ]]; then
            say "--keep: the service is still running (pid $SERVER_PID) at $BASE"
            say "  token and curl options: curl -K $WORK/curl.cfg $BASE/api/v1/"
            say "  stop it with: kill $SERVER_PID"
        else
            kill "$SERVER_PID" 2>/dev/null
            local i
            for i in $(seq 1 50); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 0.2; done
            kill -9 "$SERVER_PID" 2>/dev/null
            wait "$SERVER_PID" 2>/dev/null
        fi
    fi
    if [[ -n $WORK && $KEEP -eq 0 ]]; then rm -rf "$WORK"; fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# ---------------------------------------------------------------------------------------------------------------
# Prerequisites

for tool in curl jq git python3 tar awk; do
    command -v "$tool" >/dev/null 2>&1 || die 2 "$tool is required and was not found"
done
[[ -n $PROFILE ]] || die 2 "--profile needs an id"

if [[ -z $ATTACH ]]; then
    [[ -n $OPEN_PACKS ]] || die 2 "--open-packs <dir> is required (a checkout of spec-kitty-open-packs at the pin)"
    [[ -d $OPEN_PACKS ]] || die 2 "--open-packs $OPEN_PACKS is not a directory"
    OPEN_PACKS=$(cd "$OPEN_PACKS" && pwd)
    git -C "$OPEN_PACKS" rev-parse --git-dir >/dev/null 2>&1 || die 2 "$OPEN_PACKS is not a git checkout"
    PIN=$(git -C "$OPEN_PACKS" rev-parse HEAD)
    WANT=$(sed -n 's/^openPacksRef=//p' "$REPO/gradle.properties" | head -1)
    [[ -z $WANT || $PIN == "$WANT" ]] ||
        die 2 "the open-packs checkout is at $PIN but gradle.properties pins openPacksRef=$WANT; check out the pin"
    if [[ -z ${JAVA_HOME:-} && -d /usr/lib/jvm/temurin-25 ]]; then export JAVA_HOME=/usr/lib/jvm/temurin-25; fi
    JAVA=java
    [[ -n ${JAVA_HOME:-} ]] && JAVA="$JAVA_HOME/bin/java"
    command -v "$JAVA" >/dev/null 2>&1 || die 2 "no java found (set JAVA_HOME to a JDK 25)"
else
    PIN=$(git -C "${OPEN_PACKS:-/nonexistent}" rev-parse HEAD 2>/dev/null || echo "not given (--attach)")
fi
COMMIT=$(git -C "$REPO" rev-parse HEAD 2>/dev/null || echo unknown)
BRANCH=$(git -C "$REPO" branch --show-current 2>/dev/null || echo unknown)

WORK=$(mktemp -d "${TMPDIR:-/tmp}/service-usability-run.XXXXXX") || die 2 "cannot make a temporary directory"
chmod 700 "$WORK"
CURL_CFG="$WORK/curl.cfg"

# ---------------------------------------------------------------------------------------------------------------
# HTTP helpers. The token goes in a curl options file, never in an argument list another process could read.

http() { # method url [body-file]; sets STATUS, TIME and BODY (a file)
    local method=$1 url=$2 data=${3:-}
    BODY="$WORK/body.$((++SEQ))"
    local args=(-sS -K "$CURL_CFG" -o "$BODY" -w '%{http_code} %{time_total}' -X "$method" -H 'Accept: application/json')
    if [[ -n $data ]]; then args+=(-H 'Content-Type: application/json' --data-binary "@$data"); fi
    local result
    if ! result=$(curl "${args[@]}" "$url" 2>"$WORK/curl.err"); then
        STATUS=000
        TIME=0
        printf '{"title":"transport failure","detail":%s}' "$(jq -Rs . <"$WORK/curl.err")" >"$BODY"
        return 0
    fi
    STATUS=${result%% *}
    TIME=${result##* }
}

problem() { # the problem body of the last answer, on one line
    jq -r '[.status, .code, .title, .detail] | map(select(. != null)) | join(" | ")' "$BODY" 2>/dev/null | cut -c1-300
}

expand() { # href var=value...: fills the variables a link declares; fails if one is left unfilled
    local href=$1
    shift
    local kv k v
    for kv in "$@"; do
        k=${kv%%=*}
        v=${kv#*=}
        href=${href//\{$k\}/$v}
    done
    if [[ $href =~ \{\?([^}]*)\} ]]; then
        local names=${BASH_REMATCH[1]} query="" n
        for n in ${names//,/ }; do
            for kv in "$@"; do
                if [[ ${kv%%=*} == "$n" ]]; then query+="${query:+&}$n=${kv#*=}"; fi
            done
        done
        href=${href/\{\?*\}/${query:+?$query}}
    fi
    if [[ $href == *"{"* ]]; then printf '%s' "$href"; return 1; fi
    printf '%s' "$href"
}

follow() { # id doc relation what [var=value...]: GET the href of _links.<relation> of doc; keeps the answer in hop-<id>.json
    local id=$1 doc=$2 rel=$3 what=$4
    shift 4
    local href
    href=$(jq -r --arg r "$rel" '._links[$r].href // empty' "$doc" 2>/dev/null)
    if [[ -z $href ]]; then
        hop_fail "$id" "expected relation '$rel' in _links of $what; present: $(jq -r '(._links // {}) | keys | join(", ")' "$doc" 2>/dev/null)"
        return 1
    fi
    local expanded
    if ! expanded=$(expand "$href" "$@"); then
        hop_fail "$id" "relation '$rel' of $what is $href and declares a variable this client has no value for"
        return 1
    fi
    http GET "$BASE$expanded"
    if [[ $STATUS == 2* ]]; then
        cp "$BODY" "$WORK/hop-$id.json"
        HREF=$expanded
        hop_ok "$id" "GET $expanded -> $STATUS (relation '$rel' of $what)"
        return 0
    fi
    hop_fail "$id" "GET $expanded -> $STATUS $(problem) (relation '$rel' of $what)"
    return 1
}

find_item() { # id list-doc what: the list item for the profile, following 'next' links; leaves it in item.json
    local id=$1 doc=$2 what=$3 page=1
    while :; do
        jq -c --arg p "$PROFILE" \
            '[.items[]? | select((.artifactId // .id) == $p and (.kind // "agent_profile") == "agent_profile")][0] // empty' \
            "$doc" >"$WORK/item.json"
        if [[ -s $WORK/item.json ]]; then
            hop_ok "$id" "found agent_profile '$PROFILE' in $what (page $page)"
            return 0
        fi
        if [[ -z $(jq -r '._links.next.href // empty' "$doc") ]]; then
            hop_fail "$id" "agent_profile '$PROFILE' is not in $what ($page page(s)); it lists $(jq -r '[.items[]? | (.artifactId // .id)] | length' "$doc") item(s) on the last page"
            return 1
        fi
        page=$((page + 1))
        follow "$id" "$doc" next "page $((page - 1)) of $what" || return 1
        doc="$WORK/hop-$id.json"
    done
}

p95_ms() { # reads seconds, one per line; prints the nearest-rank 95th percentile in milliseconds
    sort -n | awk '{a[NR] = $1} END {
        if (NR == 0) { print "NA"; exit }
        r = 0.95 * NR; i = int(r); if (i < r) i++
        printf "%d", a[i] * 1000 + 0.5
    }'
}

time_reads() { # label href: READS timed GETs; leaves the p95 in milliseconds in P95_RESULT
    local label=$1 href=$2 i
    P95_RESULT="NA"
    : >"$WORK/times.$label"
    for i in $(seq 1 "$READS"); do
        http GET "$BASE$href"
        if [[ $STATUS != 2* ]]; then
            hop_fail "T$label" "timed read $i of $href -> $STATUS $(problem)"
            return 1
        fi
        echo "$TIME" >>"$WORK/times.$label"
    done
    P95_RESULT=$(p95_ms <"$WORK/times.$label")
}

# ---------------------------------------------------------------------------------------------------------------
# T045: build, start, provision

pack_name() { sed -n 's/^name: *//p' "$1/pack.yaml" | head -1 | tr -d '"'"'"; }

tree_id_of() { # the git tree id a directory would have, computed in a scratch repository
    local dir=$1 gitdir="$WORK/scratch-git.$((++SEQ))"
    git init -q --bare "$gitdir" || return 1
    (cd "$dir" && GIT_DIR="$gitdir" GIT_WORK_TREE="$dir" git -c core.autocrlf=false add -A -f . &&
        GIT_DIR="$gitdir" git write-tree)
}

register_pack() { # packId relative-path: POST /packs (filesystem source), polled to its end; leaves the status in REG
    local pack_id=$1 path=$2
    jq -nc --arg i "$pack_id" --arg p "$path" '{kind: "filesystem", packId: $i, path: $p}' >"$WORK/reg.json"
    http POST "$BASE/api/v1/packs" "$WORK/reg.json"
    if [[ $STATUS != 202 ]]; then
        REG="$BODY"
        return 1
    fi
    local self i
    self=$(jq -r '._links.self.href' "$BODY")
    for i in $(seq 1 240); do
        http GET "$BASE$self"
        REG="$BODY"
        case $(jq -r .state "$BODY") in
            succeeded) return 0 ;;
            refused) return 1 ;;
        esac
        sleep 0.5
    done
    return 1
}

refusal_lines() { # prints the refusal of the status in REG, one violation per line
    jq -r '(.problem // .) as $p |
        "    refused: \($p.status // "") \($p.code // "") \($p.detail // "")",
        ($p.violations[]? | "      - \(.pointer) [\(.code)] \(.detail)")' "$REG"
}

provision_pack() { # dir-name: registers packs/<dir-name> of the open-packs checkout, unmodified or with a named workaround
    local dir=$1
    local src="$SOURCES/$dir"
    if [[ ! -d $src ]]; then die 4 "the open-packs checkout holds no packs/$dir at $PIN"; fi
    local pack_id
    pack_id=$(pack_name "$src")
    [[ -n $pack_id ]] || die 4 "packs/$dir has no name in its pack.yaml"
    say "  pack packs/$dir declares name '$pack_id'; the registration's packId must equal it"
    if register_pack "$pack_id" "$dir"; then
        hop_ok "R-$dir" "registered '$pack_id' unmodified from packs/$dir"
        return 0
    fi
    refusal_lines
    local round removed=() staged="$dir-registrable" path
    cp -a "$src" "$SOURCES/$staged"
    for round in 1 2 3; do
        if [[ -n $(jq -r '(.problem // .).violations[]? | select(.code != "unmodelled") | .code' "$REG") ]] ||
            [[ -z $(jq -r '(.problem // .).violations[]? | .pointer' "$REG") ]]; then
            hop_fail "R-$dir" "registration of '$pack_id' refused for a reason no workaround covers"
            return 1
        fi
        while IFS= read -r path; do
            rm -rf "${SOURCES:?}/$staged/$path"
            removed+=("$path")
        done < <(jq -r '(.problem // .).violations[]? | .pointer' "$REG")
        if register_pack "$pack_id" "$staged"; then
            workaround "unmodelled-files (#451): '$pack_id' was refused over ${removed[*]}; registered a staged copy of packs/$dir without them. The checkout is untouched."
            hop_ok "R-$dir" "registered '$pack_id' from a staged copy of packs/$dir"
            return 0
        fi
        refusal_lines
    done
    hop_fail "R-$dir" "registration of '$pack_id' is still refused after the staged copy lost ${removed[*]}"
    return 1
}

provision_project_pack() {
    local src="$REPO/.kittify/charter-packs"
    if [[ ! -d $src ]]; then
        say "  no project pack at .kittify/charter-packs; skipped"
        return 0
    fi
    cp -a "$src" "$SOURCES/project"
    say "  project pack .kittify/charter-packs, as it is, with packId 'project'"
    if register_pack project project; then
        hop_ok "R-project" "registered the project pack unmodified"
        return 0
    fi
    refusal_lines
    workaround "project-pack (#452): the project pack was refused (see above) and is not registered. No edit was tried: it needs a pack.yaml, plural folders, a different graph format and more. Selections only it holds are reported under 'unheld selections'."
}

start_service() {
    if [[ -z $JAR ]]; then
        say "== build :deployable:bootJar"
        (cd "$REPO" && ./gradlew --max-workers=1 :deployable:bootJar -q) >"$WORK/gradle.log" 2>&1 ||
            { tail -20 "$WORK/gradle.log" >&2; die 3 "building :deployable:bootJar failed"; }
        JAR=$(ls "$REPO"/service/deployable/build/libs/deployable-*.jar 2>/dev/null | grep -v -- '-plain' | head -1)
    fi
    [[ -f $JAR ]] || die 3 "no jar at '$JAR'"
    local token
    token=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')
    printf 'header = "Authorization: Bearer %s"\n' "$token" >"$CURL_CFG"
    chmod 600 "$CURL_CFG"
    say "== start the service ($JAR)"
    CHARTER_WORKTREE_TOKEN=$token "$JAVA" -jar "$JAR" \
        --configRootLocation="$CONFIG" --server.port=0 \
        --charter.registration.enabled=true \
        --charter.pack-sources.filesystem.enabled=true \
        --charter.pack-sources.filesystem.root="$SOURCES" >"$WORK/service.log" 2>&1 &
    SERVER_PID=$!
    local i port=""
    for i in $(seq 1 240); do
        port=$(sed -n 's/.*Tomcat started on port \([0-9]*\).*/\1/p' "$WORK/service.log" | head -1)
        [[ -n $port ]] && break
        kill -0 "$SERVER_PID" 2>/dev/null || { grep -v JAVA_TOOL "$WORK/service.log" | grep -i -E 'ERROR|refus' | head -5 >&2; die 3 "the service stopped while starting"; }
        sleep 0.5
    done
    [[ -n $port ]] || die 3 "the service did not report a port in 120 s"
    BASE="http://127.0.0.1:$port"
    say "   listening on $BASE (pid $SERVER_PID)"
}

provision() {
    SOURCES="$WORK/pack-sources"
    CONFIG="$WORK/config"
    mkdir -p "$SOURCES" "$CONFIG/packs"
    say "== open-packs checkout $OPEN_PACKS at $PIN"
    git -C "$OPEN_PACKS" archive --format=tar HEAD packs/built-in | tar -x -C "$CONFIG/packs" --strip-components=1 ||
        die 4 "cannot extract packs/built-in at $PIN"
    local want have
    want=$(git -C "$OPEN_PACKS" rev-parse HEAD:packs/built-in)
    have=$(tree_id_of "$CONFIG/packs/built-in") || die 4 "cannot compute the tree id of the copied built-in"
    if [[ $want != "$have" ]]; then
        die 4 "the copied built-in has git tree $have but $PIN holds $want; the copy is not the pinned pack"
    fi
    say "   built-in copied into <config-root>/packs/, git tree $have = HEAD:packs/built-in"
    local d
    for d in ${EXTRA_PACKS//,/ }; do
        git -C "$OPEN_PACKS" archive --format=tar HEAD "packs/$d" | tar -x -C "$SOURCES" --strip-components=1 ||
            die 4 "cannot extract packs/$d at $PIN"
    done
    start_service
    say "== register packs"
    for d in ${EXTRA_PACKS//,/ }; do provision_pack "$d"; done
    provision_project_pack
}

# ---------------------------------------------------------------------------------------------------------------
# T046 / T047: the activation and the walk

assert_requires() { # id doc what: SC-003, java-conventions is a requires entry of the profile
    if jq -e '[.governance[]? | select(.artifactId == "java-conventions" and .relation == "requires")] | length > 0' "$2" >/dev/null 2>&1; then
        hop_ok "$1" "SC-003: styleguide java-conventions is a requires entry of $PROFILE in $3"
    else
        hop_fail "$1" "SC-003: no requires entry java-conventions in the governance of $3"
    fi
}

check_body() { # id doc what: the profile body and an edge on every governance entry
    local id=$1 doc=$2 what=$3
    if ! jq -e --arg p "$PROFILE" '.agentProfile.profileId == $p and (.governance | type) == "array"' "$doc" >/dev/null 2>&1; then
        hop_fail "$id" "the read of $what has no agentProfile.profileId '$PROFILE' with a governance array"
        return 1
    fi
    local without
    without=$(jq '[.governance[] | select((.edge | type) != "object")] | length' "$doc")
    if [[ $without -ne 0 ]]; then
        hop_fail "$id" "$without governance entr(ies) of $what carry no edge object"
        return 1
    fi
    hop_ok "$id" "$what has the body of '$PROFILE' and $(jq '.governance | length' "$doc") governance entries, each with an edge"
}

walk_entries() { # id doc what relation...: for each governance entry, follow each relation named
    local id=$1 doc=$2 what=$3
    shift 3
    local n=0 held_false=0 failed_before=$HOP_FAIL line rel
    while IFS= read -r line; do
        printf '%s\n' "$line" >"$WORK/entry.json"
        if [[ $(jq -r '.held' "$WORK/entry.json") == "false" ]]; then
            held_false=$((held_false + 1))
            continue
        fi
        n=$((n + 1))
        for rel in "$@"; do
            local label="$id $(jq -r '.kind + "/" + .artifactId' "$WORK/entry.json")"
            if [[ -z $(jq -r --arg r "$rel" '._links[$r].href // empty' "$WORK/entry.json") ]]; then
                # one line per relation is enough; the counts say the rest
                if [[ -z ${MISSING[$id/$rel]:-} ]]; then
                    MISSING[$id/$rel]=1
                    hop_fail "$id" "expected relation '$rel' in _links of a governance entry of $what (first seen on $(jq -r '.kind + "/" + .artifactId' "$WORK/entry.json"))"
                else
                    HOP_FAIL=$((HOP_FAIL + 1))
                fi
                continue
            fi
            follow_quiet "$id" "$WORK/entry.json" "$rel" "$label" || true
        done
    done < <(jq -c '.governance[]' "$doc")
    say "  (${id}: $n held entries of $what walked; $held_false with held=false have no pack to link to; $((HOP_FAIL - failed_before)) failed)"
}

follow_quiet() { # like follow, but prints only a failure; counts the successes
    local id=$1 doc=$2 rel=$3 what=$4
    local href
    href=$(jq -r --arg r "$rel" '._links[$r].href' "$doc")
    http GET "$BASE$href"
    if [[ $STATUS == 2* ]]; then
        HOP_OK=$((HOP_OK + 1))
        return 0
    fi
    hop_fail "$id" "GET $href -> $STATUS $(problem) (relation '$rel' of $what)"
    return 1
}

walk_pack_scoped() {
    say "== walk 1: pack-scoped (root -> artifacts -> $PROFILE -> governance)"
    local root="$WORK/root.json"
    cp "$ROOT_BODY" "$root"
    follow P2 "$root" artifacts "the API root" packId=built-in kind=agent_profile || return 1
    find_item P3 "$WORK/hop-P2.json" "the agent_profile list of pack built-in" || return 1
    follow P4 "$WORK/item.json" self "the $PROFILE list item" || return 1
    local read="$WORK/hop-P4.json" read_href=$HREF
    check_body P5 "$read" "the pack-scoped read" || return 1
    assert_requires P6 "$read" "the pack-scoped read"
    walk_entries P7 "$read" "the pack-scoped read" artifact
    say "  timing $READS pack-scoped reads of $read_href"
    time_reads pack "$read_href"
    P95_PACK=$P95_RESULT
    check_p95 TP "$P95_PACK" "pack-scoped"
}

check_p95() { # id ms what
    if [[ $2 == NA ]]; then return 1; fi
    if [[ $2 -ge $P95_LIMIT_MS ]]; then
        hop_fail "$1" "NFR-003: p95 of $READS $3 profile reads is $2 ms, not under $P95_LIMIT_MS ms"
        return 1
    fi
    hop_ok "$1" "NFR-003: p95 of $READS $3 profile reads is $2 ms (limit $P95_LIMIT_MS ms)"
}

make_document() { python3 -I "$HERE/charter-to-activation.py" --profile "$PROFILE" "$@" >"$WORK/activation.json"; }

create_activation() { # href: POST the document, applying the one workaround a 422 allows
    local href=$1 drops=() v kind id idx
    make_document || { hop_fail A2 "charter-to-activation.py failed"; return 1; }
    http POST "$BASE$href" "$WORK/activation.json"
    if [[ $STATUS == 422 && $(jq -r .code "$BODY") == activation_invalid ]]; then
        say "  the service refused the document: $(problem)"
        jq -r '.violations[]? | "    - \(.pointer) [\(.code)] \(.detail)"' "$BODY"
        local lines
        lines=$(jq -r '.violations[]? | select(.code == "unknown_artifact") | .pointer' "$BODY")
        local named total
        named=$(grep -c . <<<"$lines")
        total=$(jq '.violations | length' "$BODY")
        if [[ $STRICT -eq 1 || -z $lines || $named -ne $total ]]; then
            hop_fail A2 "POST $href -> $STATUS $(problem); --strict, or a violation other than unknown_artifact, so nothing is dropped"
            return 1
        fi
        while IFS= read -r v; do
            kind=$(cut -d/ -f3 <<<"$v")
            idx=$(cut -d/ -f4 <<<"$v")
            id=$(jq -r --arg k "$kind" --argjson i "$idx" '.selected[$k][$i]' "$WORK/activation.json")
            drops+=(--drop "$kind:$id")
            DROPPED+=("$kind:$id")
        done <<<"$lines"
        workaround "unheld-selections: ${#DROPPED[@]} selection(s) no registered pack holds were left out of the activation document: ${DROPPED[*]}. Every one is named by the service's own 422."
        make_document "${drops[@]}" || { hop_fail A2 "charter-to-activation.py failed"; return 1; }
        http POST "$BASE$href" "$WORK/activation.json"
    fi
    if [[ $STATUS != 201 && $STATUS != 200 ]]; then
        hop_fail A2 "POST $href -> $STATUS $(problem)"
        return 1
    fi
    cp "$BODY" "$WORK/hop-A2.json"
    hop_ok A2 "POST $href -> $STATUS (relation 'activations' of the API root), activation $(jq -r .activationId "$BODY")"
}

walk_activation() {
    say "== walk 2: under an activation (root -> activations -> activation -> artifacts -> $PROFILE -> governance)"
    local root="$WORK/root.json" href method
    href=$(jq -r '._links.activations.href // empty' "$root")
    if [[ -z $href ]]; then
        hop_fail A1 "expected relation 'activations' (method POST) in _links of the API root; present: $(jq -r '(._links // {}) | keys | join(", ")' "$root")"
        return 1
    fi
    method=$(jq -r '._links.activations.method // empty' "$root")
    if [[ $method != POST ]]; then
        hop_fail A1 "relation 'activations' of the API root declares method '${method:-none}', expected POST"
        return 1
    fi
    hop_ok A1 "relation 'activations' of the API root, method POST, $href"
    create_activation "$href" || return 1
    follow A3 "$WORK/hop-A2.json" self "the created activation" || return 1
    follow A4 "$WORK/hop-A3.json" artifacts "the activation" kind=agent_profile || return 1
    find_item A5 "$WORK/hop-A4.json" "the effective artifacts of the activation" || return 1
    follow A6 "$WORK/item.json" effective-artifact "the $PROFILE effective item" || return 1
    local read="$WORK/hop-A6.json" read_href=$HREF
    check_body A7 "$read" "the read under the activation" || return 1
    assert_requires A8 "$read" "the read under the activation"
    walk_entries A9 "$read" "the read under the activation" effective-artifact artifact
    say "  timing $READS reads of $read_href"
    time_reads act "$read_href"
    P95_ACT=$P95_RESULT
    check_p95 TA "$P95_ACT" "activation"
}

# ---------------------------------------------------------------------------------------------------------------

summary() {
    say ""
    say "== summary"
    say "  main commit        : $COMMIT ($BRANCH)"
    say "  open-packs pin     : $PIN"
    say "  hops followed      : $HOP_OK ok, $HOP_FAIL failed"
    local f
    for f in "${HOPS_FAILED[@]:-}"; do [[ -n $f ]] && say "    failed: $f"; done
    say "  workarounds        : ${#WORKAROUNDS[@]}"
    for f in "${WORKAROUNDS[@]:-}"; do [[ -n $f ]] && say "    - $f"; done
    say "  unheld selections  : ${#DROPPED[@]}${DROPPED[*]:+ (${DROPPED[*]})}"
    say "  p95 pack-scoped    : $P95_PACK ms; under activation: $P95_ACT ms (limit $P95_LIMIT_MS ms, $READS reads each)"
    local verdict=PASS
    if [[ $HOP_FAIL -gt 0 ]]; then verdict=FAIL; fi
    say "RESULT $verdict hops=$HOP_OK/$((HOP_OK + HOP_FAIL)) failed=$HOP_FAIL workarounds=${#WORKAROUNDS[@]} p95=${P95_PACK}/${P95_ACT}ms commit=${COMMIT:0:8} pin=$([[ $PIN =~ ^[0-9a-f]{40}$ ]] && echo "${PIN:0:8}" || echo none)"
    [[ $verdict == PASS ]]
}

main() {
    if [[ -n $ATTACH ]]; then
        BASE=${ATTACH%/}
        [[ -n ${CHARTER_WORKTREE_TOKEN:-} ]] || die 2 "--attach needs the service's token in CHARTER_WORKTREE_TOKEN"
        printf 'header = "Authorization: Bearer %s"\n' "$CHARTER_WORKTREE_TOKEN" >"$CURL_CFG"
        chmod 600 "$CURL_CFG"
    else
        provision
    fi
    say "== walk from GET /api/v1/"
    http GET "$BASE/api/v1/"
    ROOT_BODY="$BODY"
    if [[ $STATUS != 2* ]]; then
        hop_fail P1 "GET /api/v1/ -> $STATUS $(problem)"
    else
        hop_ok P1 "GET /api/v1/ -> $STATUS (the entry point)"
        walk_pack_scoped
        walk_activation
    fi
    summary
}

main
