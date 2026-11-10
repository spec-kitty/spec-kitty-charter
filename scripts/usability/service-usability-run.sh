#!/usr/bin/env bash
# Service usability run (FR-011, SC-002, NFR-003): a client that starts the charter service, loads the open-packs,
# activates this charter and reads an agent profile and the software-dev mission type by following links only.
#
#   scripts/usability/service-usability-run.sh --open-packs ../spec-kitty-open-packs [--profile java-jenny]
#       [--jar <path>] [--keep] [--packs internal,qa] [--strict] [--attach <base-url>]
#   scripts/usability/service-usability-run.sh --self-check
#
# It exits 0 only if every hop of the walk answered 2xx and the profile reads were fast enough. The exit codes are
# in scripts/usability/README.md. The last line of the output is a one-line summary.

# No -e on purpose: the walk collects the failure of each hop and reports them all at the end.
set -u -o pipefail
if ((BASH_VERSINFO[0] < 4 || (BASH_VERSINFO[0] == 4 && BASH_VERSINFO[1] < 2))); then
    echo "error: bash 4.2 or later is required (this is ${BASH_VERSION}); on macOS install bash and GNU coreutils" >&2
    exit 2
fi
# bash 5.2 turns patsub_replacement on, which makes an unquoted & in a ${x/p/r} replacement mean "the match".
shopt -u patsub_replacement 2>/dev/null || true

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
SELF_CHECK=0

usage() {
    if [[ ${1:-2} -eq 0 ]]; then
        sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    else
        sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
    fi
    exit "${1:-2}"
}

needs_value() { # option remaining-argument-count next-argument: an option that takes a value must be given one
    if [[ $2 -lt 2 || -z $3 || $3 == --* ]]; then
        echo "error: $1 needs a value" >&2
        usage 2
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --open-packs|--profile|--jar|--attach|--packs) needs_value "$1" "$#" "${2:-}" ;;
    esac
    case "$1" in
        --open-packs) OPEN_PACKS=$2; shift 2 ;;
        --profile) PROFILE=$2; shift 2 ;;
        --jar) JAR=$2; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --strict) STRICT=1; shift ;;
        --attach) ATTACH=$2; shift 2 ;;
        --packs) EXTRA_PACKS=$2; shift 2 ;;
        --self-check) SELF_CHECK=1; shift ;;
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
declare -A SERVED=()     # kind -> yes|no: does the service have a pack-scoped read for the kind (probed, cached)
declare -A UNLINKED=()   # "<hop> <relation> <kind>" -> entries left unlinked because their kind is not served
WORKAROUNDS=()
DROPPED=()
PROBE=""
REFUSAL=""
URL=""
ENC=""
P95_PACK="NA"
P95_ACT="NA"
P95_RESULT="NA"
SERVER_PID=""
WORK=""
WORK_MADE=""
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

remove_work() { # rm -rf of the temporary directory, only if it is the one this script made
    if [[ $WORK == "$WORK_MADE" && -n $WORK_MADE && ! -L $WORK && -f $WORK/.made-by-service-usability-run &&
        $(basename "$WORK") == service-usability-run.* ]]; then
        rm -rf -- "$WORK"
    else
        printf 'error: not removing %s: it is not the temporary directory this script made\n' "$WORK" >&2
    fi
}

remove_inside() { # path: rm -rf of a path whose parent resolves to somewhere inside the temporary directory
    # Only the parent is resolved: a symlink as the last component is removed as a link, never followed to its target.
    local parent base target
    base=$(basename -- "$1")
    if [[ -z $WORK_MADE || $base == / || $base == . || $base == .. ]] ||
        ! parent=$(realpath -e -- "$(dirname -- "$1")" 2>/dev/null); then
        printf 'error: not removing %s: it cannot be resolved\n' "$1" >&2
        return 1
    fi
    target=${parent%/}/$base
    case $target in
        "$(realpath -- "$WORK_MADE")"/?*) rm -rf -- "$target" ;;
        *) printf 'error: not removing %s: it is outside the temporary directory %s\n' "$1" "$WORK_MADE" >&2; return 1 ;;
    esac
}

KEEP_HINTED=0
keep_hint() {
    [[ $KEEP_HINTED -eq 0 && $KEEP -eq 1 && -n $SERVER_PID ]] || return 0
    KEEP_HINTED=1
    say "--keep: the service is still running (pid $SERVER_PID) at $BASE"
    say "  token and curl options: curl -K $WORK/curl.cfg $BASE/api/v1/"
    say "  stop it with: kill $SERVER_PID"
}

cleanup() {
    local status=$?
    if [[ -n $SERVER_PID ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        if [[ $KEEP -eq 1 ]]; then
            keep_hint # a no-op if the summary has already printed it, before the RESULT line
        else
            kill "$SERVER_PID" 2>/dev/null
            local i
            for i in $(seq 1 50); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 0.2; done
            kill -9 "$SERVER_PID" 2>/dev/null
            wait "$SERVER_PID" 2>/dev/null
        fi
    fi
    if [[ -n $WORK && $KEEP -eq 0 ]]; then remove_work; fi
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

if [[ $SELF_CHECK -eq 1 ]]; then
    :
elif [[ -z $ATTACH ]]; then
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
# The client tree: what Gradle builds when there is no --jar. "-dirty" means uncommitted changes were in the build.
COMMIT=$(git -C "$REPO" describe --always --dirty --abbrev=40 2>/dev/null || echo unknown)
JAR_SHA=""
BRANCH=$(git -C "$REPO" branch --show-current 2>/dev/null || echo unknown)

WORK=$(mktemp -d "${TMPDIR:-/tmp}/service-usability-run.XXXXXX") || die 2 "cannot make a temporary directory"
chmod 700 "$WORK"
WORK_MADE=$WORK
: >"$WORK/.made-by-service-usability-run"
CURL_CFG="$WORK/curl.cfg"

# ---------------------------------------------------------------------------------------------------------------
# HTTP helpers. The token goes in a curl options file, never in an argument list another process could read.

http() { # method url [body-file]; sets STATUS, TIME and BODY (a file)
    local method=$1 url=$2 data=${3:-}
    BODY="$WORK/body.$((++SEQ))"
    local args=(-g -sS -K "$CURL_CFG" -o "$BODY" -w '%{http_code} %{time_total}' -X "$method" -H 'Accept: application/json')
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

urlenc() { # string: percent-encodes all but the unreserved characters (RFC 3986, RFC 6570); the result is in ENC
    local LC_ALL=C s=$1 out="" c i
    for ((i = 0; i < ${#s}; i++)); do
        c=${s:i:1}
        case $c in
            [A-Za-z0-9._~-]) out+=$c ;;
            *) printf -v c '%%%02X' "'$c"; out+=$c ;;
        esac
    done
    ENC=$out
}

expand() { # href var=value...: fills the variables a link declares; fails if one is left unfilled
    local href=$1 orig=$1
    shift
    local kv k v
    for kv in "$@"; do
        k=${kv%%=*}
        urlenc "${kv#*=}"
        v=$ENC
        href=${href//"{$k}"/"$v"}
    done
    if [[ $href == *"{?"*"}"* ]]; then
        # no ${//} here: the pieces are cut and joined, so no & of a query can be read as "the match"
        local head=${href%%"{?"*} tail=${href#*"{?"} names query="" n
        names=${tail%%"}"*}
        tail=${tail#*"}"}
        for n in ${names//,/ }; do
            n=${n%\*} # {?kind*}: the explode modifier; every value here is a single string, so it expands as plain
            if [[ ! $n =~ ^[A-Za-z0-9_]+$ ]]; then
                printf 'error: URI template %s: the variable name "%s" is not supported\n' "$orig" "$n" >&2
                return 1
            fi
            for kv in "$@"; do
                if [[ ${kv%%=*} == "$n" ]]; then
                    urlenc "${kv#*=}"
                    query+="${query:+&}$n=$ENC"
                fi
            done
        done
        href="$head${query:+?$query}$tail"
    fi
    printf '%s' "$href"
    if [[ $href == *"{"* || $href == *"}"* ]]; then
        printf 'error: URI template %s: left unfilled or not supported (only {var} and {?var,var*} are): %s\n' "$orig" "$href" >&2
        return 1
    fi
}

resolve() { # href: sets URL, the absolute URL to request; fails with REFUSAL when the href must not be followed
    case $1 in
        "$BASE"/*) URL=$1 ;;
        http://* | https://* | //*)
            REFUSAL="the href $1 is not on $BASE; it was not followed, so the token stays with the service"
            return 1
            ;;
        /*) URL=$BASE$1 ;;
        *)
            REFUSAL="the href '$1' is neither an absolute URL on $BASE nor a path starting with /; it was not followed"
            return 1
            ;;
    esac
}

http_href() { # method href [body-file]: http, for an href taken from a link; an href that is refused answers 000
    local method=$1 href=$2 data=${3:-}
    if ! resolve "$href"; then
        BODY="$WORK/body.$((++SEQ))"
        STATUS=000
        TIME=0
        jq -nc --arg d "$REFUSAL" '{title: "link not followed", detail: $d}' >"$BODY"
        return 0
    fi
    http "$method" "$URL" "$data"
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
    http_href GET "$expanded"
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
        http_href GET "$href"
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
        http_href GET "$self"
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
            case /$path/ in
                /../* | */../* | //*) hop_fail "R-$dir" "the service named the path '$path', which is absolute or climbs out; not removed"; return 1 ;;
            esac
            remove_inside "$SOURCES/$staged/$path" || { hop_fail "R-$dir" "refused to remove '$path' outside the temporary directory"; return 1; }
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
        JAR=$(ls -t "$REPO"/service/deployable/build/libs/deployable-*.jar 2>/dev/null | grep -v -- '-plain' | head -1)
    fi
    [[ -f $JAR ]] || die 3 "no jar at '$JAR'"
    JAR_SHA=$(sha256sum -- "$JAR" 2>/dev/null | cut -c1-64)
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

kind_served() { # kind pack-id: 0 = the kind has a pack-scoped read, 1 = it has none, 2 = cannot tell (PROBE says why)
    # A link-only client asks the service: the root 'artifacts' link with this packId and kind answers 200 for a kind the
    # service serves and 400 for one it does not (contracts/charter/paths/packs_packId_artifacts.yaml). Cached per kind.
    local kind=$1 pack=$2 href
    case ${SERVED[$kind]:-} in
        yes) return 0 ;;
        no) return 1 ;;
    esac
    href=$(jq -r '._links.artifacts.href // empty' "$WORK/root.json")
    if [[ -z $href ]] || ! href=$(expand "$href" "packId=$pack" "kind=$kind"); then
        PROBE="the API root has no usable 'artifacts' relation to ask with"
        return 2
    fi
    http_href GET "$href"
    case $STATUS in
        200) SERVED[$kind]=yes; return 0 ;;
        400) SERVED[$kind]=no; say "  kind '$kind' is not served: GET $href -> 400"; return 1 ;;
    esac
    PROBE="GET $href -> $STATUS $(problem)"
    return 2
}

walk_entries() { # id doc what relation...: for each governance entry, follow each relation named
    local id=$1 doc=$2 what=$3
    shift 3
    local n=0 held_false=0 failed_before=$HOP_FAIL line rel kind pack key rc unlinked=0
    while IFS= read -r line; do
        printf '%s\n' "$line" >"$WORK/entry.json"
        if [[ $(jq -r '.held' "$WORK/entry.json") == "false" ]]; then
            held_false=$((held_false + 1))
            continue
        fi
        n=$((n + 1))
        kind=$(jq -r '.kind' "$WORK/entry.json")
        pack=$(jq -r '.packId // ""' "$WORK/entry.json")
        for rel in "$@"; do
            local label="$id $kind/$(jq -r '.artifactId' "$WORK/entry.json")"
            if [[ -z $(jq -r --arg r "$rel" '._links[$r].href // empty' "$WORK/entry.json") ]]; then
                kind_served "$kind" "$pack"
                rc=$?
                if [[ $rc -eq 1 ]]; then
                    # the contract links this relation only for a served kind; counted and reported, never skipped silently
                    key="$id $rel $kind"
                    UNLINKED[$key]=$((${UNLINKED[$key]:-0} + 1))
                    unlinked=$((unlinked + 1))
                    continue
                fi
                # a served kind without the link fails, and so does a kind the client cannot classify; one line per
                # relation is enough, the counts say the rest
                if [[ -z ${MISSING[$id/$rel]:-} ]]; then
                    MISSING[$id/$rel]=1
                    if [[ $rc -eq 0 ]]; then
                        hop_fail "$id" "expected relation '$rel' in _links of a governance entry of $what, whose kind '$kind' is served (first seen on $kind/$(jq -r '.artifactId' "$WORK/entry.json"))"
                    else
                        hop_fail "$id" "relation '$rel' is missing on $kind/$(jq -r '.artifactId' "$WORK/entry.json") and the client cannot tell whether the kind is served: $PROBE"
                    fi
                else
                    HOP_FAIL=$((HOP_FAIL + 1))
                fi
                continue
            fi
            follow_quiet "$id" "$WORK/entry.json" "$rel" "$label" || true
        done
    done < <(jq -c '.governance[]' "$doc")
    local summary_unlinked="" k
    while IFS= read -r k; do
        [[ -n $k ]] && summary_unlinked+="; not linked, kind not served: ${k#"$id "} x${UNLINKED[$k]}"
    done < <(printf '%s\n' "${!UNLINKED[@]}" | grep -F -- "$id " | sort)
    say "  (${id}: $n held entries of $what walked; $held_false with held=false have no pack to link to; $unlinked relation(s) not linked because their kind is not served${summary_unlinked}; $((HOP_FAIL - failed_before)) failed)"
}

follow_quiet() { # like follow, but prints only a failure; counts the successes
    local id=$1 doc=$2 rel=$3 what=$4
    local href
    href=$(jq -r --arg r "$rel" '._links[$r].href' "$doc")
    http_href GET "$href"
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
    http_href POST "$href" "$WORK/activation.json"
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
        http_href POST "$href" "$WORK/activation.json"
    fi
    if [[ $STATUS != 201 && $STATUS != 200 ]]; then
        hop_fail A2 "POST $href -> $STATUS $(problem)"
        return 1
    fi
    cp "$BODY" "$WORK/hop-A2.json"
    hop_ok A2 "POST $href -> $STATUS (relation 'activations' of the API root), activation $(jq -r .activationId "$BODY")"
}

walk_activation() {
    say "== walk 2: under an activation (root -> activations -> activation -> self -> artifacts -> $PROFILE -> governance)"
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
# Mission type walk (#99, SC-001 of mission-type-reads): software-dev, its 13 steps, the 5 step contracts bound to them,
# the 2 skeletons steps bind and the 4 skeletons the type lists, pack-scoped and under the activation. Links only.

MT_ID=software-dev
MT_PACK=built-in
MT_SEQUENCE="discovery specify plan tasks analyze implement review accept"
MT_ON_DEMAND="charter tasks-finalize tasks-outline tasks-packages retrospect"
MT_CONTRACTS="implement plan review specify tasks"
MT_BOUND="plan:plan-template.md specify:spec-template.md"
MT_SKELETONS="plan-template.md spec-template.md task-prompt-template.md tasks-template.md"
MT_ACTIONS="specify"
MT_LINES=()
MT_FOUND_STEPS=""
MT_FOUND_CONTRACTS=""
MT_FOUND_BOUND=""
MT_FOUND_SKELETONS=""

words_minus() { # "a b c" "b": the words of the first list that are not in the second, sorted, one space apart
    local w out=()
    for w in $1; do
        [[ " $2 " == *" $w "* ]] || out+=("$w")
    done
    printf '%s\n' "${out[@]}" | sed '/^$/d' | sort | tr '\n' ' ' | sed 's/ $//'
}

words_count() { set -f; set -- $1; set +f; printf '%s' "$#"; }

mt_steps() { # mission-type-doc: one line per step, "<id> <onDemand>", in the order the body lists them
    jq -r '.missionType.steps[]? | "\(.id) \(.onDemand // false)"' "$1"
}

find_artifact() { # id list-doc what kind artifact-id: the list item, following 'next' links; leaves it in item.json
    local id=$1 doc=$2 what=$3 kind=$4 aid=$5 page=1
    while :; do
        jq -c --arg a "$aid" --arg k "$kind" \
            '[.items[]? | select((.artifactId // .id) == $a and (.kind // $k) == $k)][0] // empty' "$doc" >"$WORK/item.json"
        if [[ -s $WORK/item.json ]]; then
            hop_ok "$id" "found $kind '$aid' in $what (page $page)"
            return 0
        fi
        if [[ -z $(jq -r '._links.next.href // empty' "$doc") ]]; then
            hop_fail "$id" "$kind '$aid' is not in $what ($page page(s))"
            return 1
        fi
        page=$((page + 1))
        follow "$id" "$doc" next "page $((page - 1)) of $what" || return 1
        doc="$WORK/hop-$id.json"
    done
}

mt_expect() { # id what found expected relation: fails, naming the relation, for each expected item not reached
    local id=$1 what=$2 found=$3 expected=$4 rel=$5 missing extra
    missing=$(words_minus "$expected" "$found")
    extra=$(words_minus "$found" "$expected")
    if [[ -z $missing && -z $extra ]]; then
        hop_ok "$id" "$what: $(words_count "$found")/$(words_count "$expected") reached"
        return 0
    fi
    hop_fail "$id" "$what: $(words_count "$found")/$(words_count "$expected") reached${missing:+; expected relation '$rel' not reached for: $missing}${extra:+; not expected: $extra}"
    return 1
}

walk_mt_body() { # prefix doc scope: the type's steps, their step contracts and bound skeletons, the listed skeletons
    local p=$1 doc=$2 scope=$3 line sid on_demand seq="" ond="" n=0 out sk
    MT_FOUND_STEPS=""
    MT_FOUND_CONTRACTS=""
    MT_FOUND_BOUND=""
    MT_FOUND_SKELETONS=""
    if ! jq -e --arg t "$MT_ID" '.kind == "mission_type" and .missionType.id == $t' "$doc" >/dev/null 2>&1; then
        hop_fail "${p}4" "the read of $MT_ID $scope is no mission_type body with missionType.id '$MT_ID'"
        return 1
    fi
    hop_ok "${p}4" "$MT_ID $scope: a mission_type body held by pack '$(jq -r '.packId' "$doc")', $(jq '.missionType.steps | length' "$doc") steps, $(jq '.missionType.skeletons | length' "$doc") skeletons"
    while read -r sid on_demand; do
        if [[ $on_demand == true ]]; then ond+="${ond:+ }$sid"; else seq+="${seq:+ }$sid"; fi
    done < <(mt_steps "$doc")
    if [[ $seq == "$MT_SEQUENCE" ]]; then
        hop_ok "${p}5" "the 8 steps in sequence, in order: $seq"
    else
        hop_fail "${p}5" "steps in sequence are [$seq], expected [$MT_SEQUENCE] in that order"
    fi
    mt_expect "${p}5" "steps on demand" "$ond" "$MT_ON_DEMAND" "steps" || true
    while IFS= read -r line; do
        n=$((n + 1))
        printf '%s\n' "$line" >"$WORK/mt-step.json"
        sid=$(jq -r '.id' "$WORK/mt-step.json")
        follow "${p}6.$sid" "$WORK/mt-step.json" self "step $sid of $MT_ID $scope" || continue
        out="$WORK/hop-${p}6.$sid.json"
        if [[ $(jq -r '.id' "$out") != "$sid" ]]; then
            hop_fail "${p}6.$sid" "the step read answered id '$(jq -r '.id' "$out")', expected '$sid'"
            continue
        fi
        MT_FOUND_STEPS+="${MT_FOUND_STEPS:+ }$sid"
        if [[ -n $(jq -r '._links["step-contract"].href // empty' "$out") ]]; then
            follow "${p}7.$sid" "$out" step-contract "step $sid $scope" && MT_FOUND_CONTRACTS+="${MT_FOUND_CONTRACTS:+ }$sid"
        fi
        local i=0 outputs
        outputs=$(jq '[.expectedOutputArtifacts[]? | select(._links.skeleton.href != null)] | length' "$out")
        for ((i = 0; i < outputs; i++)); do
            jq -c --argjson i "$i" '[.expectedOutputArtifacts[] | select(._links.skeleton.href != null)][$i]' "$out" >"$WORK/mt-output.json"
            if follow "${p}8.$sid.$i" "$WORK/mt-output.json" skeleton "an expected output of step $sid $scope"; then
                sk=$(jq -r '.name // empty' "$WORK/hop-${p}8.$sid.$i.json")
                MT_FOUND_BOUND+="${MT_FOUND_BOUND:+ }$sid:${sk:-<no name>}"
            fi
        done
    done < <(jq -c '.missionType.steps[]?' "$doc")
    mt_expect "${p}9" "steps read $scope" "$MT_FOUND_STEPS" "$MT_SEQUENCE $MT_ON_DEMAND" self || true
    mt_expect "${p}9" "step contracts bound to steps $scope" "$MT_FOUND_CONTRACTS" "$MT_CONTRACTS" step-contract || true
    mt_expect "${p}9" "skeletons bound by steps $scope" "$MT_FOUND_BOUND" "$MT_BOUND" skeleton || true
    while IFS= read -r line; do
        printf '%s\n' "$line" >"$WORK/mt-skeleton.json"
        sk=$(jq -r '.name' "$WORK/mt-skeleton.json")
        if follow "${p}10.$sk" "$WORK/mt-skeleton.json" self "skeleton $sk of $MT_ID $scope"; then
            if [[ $(jq -r '.name // empty' "$WORK/hop-${p}10.$sk.json") == "$sk" ]]; then
                MT_FOUND_SKELETONS+="${MT_FOUND_SKELETONS:+ }$sk"
            else
                hop_fail "${p}10.$sk" "the skeleton read answered name '$(jq -r '.name // empty' "$WORK/hop-${p}10.$sk.json")', expected '$sk'"
            fi
        fi
    done < <(jq -c '.missionType.skeletons[]?' "$doc")
    mt_expect "${p}11" "skeletons $MT_ID lists $scope" "$MT_FOUND_SKELETONS" "$MT_SKELETONS" self || true
}

mt_line() { # scope ok-before fail-before: the section's line for the summary
    MT_LINES+=("MISSION $1 type=$MT_ID steps=$(words_count "$MT_FOUND_STEPS")/13 contracts=$(words_count "$MT_FOUND_CONTRACTS")/5 bound-skeletons=$(words_count "$MT_FOUND_BOUND")/2 skeletons=$(words_count "$MT_FOUND_SKELETONS")/4 hops=$((HOP_OK - $2))/$((HOP_OK - $2 + HOP_FAIL - $3))")
}

walk_mt_pack() {
    say "== walk 3: mission type, pack-scoped (root -> artifacts -> $MT_ID -> steps -> step contracts -> skeletons)"
    local ok=$HOP_OK failed=$HOP_FAIL
    MT_FOUND_STEPS="" MT_FOUND_CONTRACTS="" MT_FOUND_BOUND="" MT_FOUND_SKELETONS=""
    if follow M1 "$WORK/root.json" artifacts "the API root" "packId=$MT_PACK" kind=mission_type &&
        find_artifact M2 "$WORK/hop-M1.json" "the mission_type list of pack $MT_PACK" mission_type "$MT_ID" &&
        follow M3 "$WORK/item.json" self "the $MT_ID list item"; then
        walk_mt_body M "$WORK/hop-M3.json" "in pack $MT_PACK"
    fi
    mt_line pack "$ok" "$failed"
}

mt_graph_and_context() { # the action nodes of the graph and the action context, each linking its step read
    local act="$WORK/hop-A3.json" self href node sid n=0 found=""
    self=$(jq -r '._links.self.href // empty' "$act")
    href=$(jq -r '._links.graph.href // empty' "$act")
    if [[ -z $href ]]; then
        [[ -n $self ]] || { hop_fail N12 "expected relation 'self' in _links of the activation"; return 1; }
        href="$self/graph"
        workaround "unlinked-graph-and-context: the activation links neither 'graph' nor an action context (its _links: $(jq -r '._links | keys | join(", ")' "$act")); the walk took both paths from the contract (/graph and /mission-types/{missionType}/actions/{action}/context, appended to the activation's self href). Only these two hops build a path; every step read beyond them is followed."
    fi
    http_href GET "$href"
    if [[ $STATUS != 2* ]]; then
        hop_fail N12 "GET $href -> $STATUS $(problem) (the effective graph)"
        return 1
    fi
    cp "$BODY" "$WORK/hop-N12.json"
    hop_ok N12 "GET $href -> $STATUS (the effective graph, $(jq '.nodes | length' "$BODY") nodes)"
    while IFS= read -r node; do
        printf '%s\n' "$node" >"$WORK/mt-node.json"
        sid=$(jq -r '.urn | sub("^action:[^/]*/"; "")' "$WORK/mt-node.json")
        n=$((n + 1))
        if follow_quiet N13 "$WORK/mt-node.json" effective-artifact "the graph node action:$MT_ID/$sid" 2>/dev/null; then
            [[ $(jq -r '.id' "$BODY") == "$sid" ]] && found+="${found:+ }$sid" ||
                hop_fail N13 "the step read of node action:$MT_ID/$sid answered id '$(jq -r '.id' "$BODY")'"
        fi
    done < <(jq -c --arg p "action:$MT_ID/" '.nodes[]? | select(.urn | startswith($p))' "$WORK/hop-N12.json")
    local without
    without=$(jq --arg p "action:$MT_ID/" '[.nodes[]? | select((.urn | startswith($p)) and (._links["effective-artifact"].href == null))] | length' "$WORK/hop-N12.json")
    if [[ $without -ne 0 ]]; then
        hop_fail N13 "$without action node(s) of $MT_ID carry no relation 'effective-artifact'"
    fi
    say "  the graph holds $n action node(s) of $MT_ID; their 'effective-artifact' step reads answered: ${found:-none}"
    mt_expect N13 "action nodes of the graph linking their step ($MT_ACTIONS required)" \
        "$(for sid in $found; do [[ " $MT_ACTIONS " == *" $sid "* ]] && printf '%s ' "$sid"; done)" "$MT_ACTIONS" effective-artifact || true
    for sid in $MT_ACTIONS; do
        href="$self/mission-types/$MT_ID/actions/$sid/context"
        http_href GET "$href"
        if [[ $STATUS != 2* ]]; then
            hop_fail "N14.$sid" "GET $href -> $STATUS $(problem) (the action context of $sid)"
            continue
        fi
        cp "$BODY" "$WORK/hop-N14.$sid.json"
        hop_ok "N14.$sid" "GET $href -> $STATUS (the action context of $MT_ID/$sid, $(jq '.artifacts | length' "$BODY") artifacts)"
        local rel
        for rel in effective-artifact artifact; do
            if follow "N15.$sid.$rel" "$WORK/hop-N14.$sid.json" "$rel" "the action context of $sid"; then
                [[ $(jq -r '.id' "$WORK/hop-N15.$sid.$rel.json") == "$sid" ]] ||
                    hop_fail "N15.$sid.$rel" "the step read answered id '$(jq -r '.id' "$WORK/hop-N15.$sid.$rel.json")', expected '$sid'"
            fi
        done
    done
}

walk_mt_activation() {
    say "== walk 4: mission type under the activation (activation -> artifacts -> $MT_ID -> steps -> step contracts -> skeletons; graph and action context)"
    local ok=$HOP_OK failed=$HOP_FAIL
    MT_FOUND_STEPS="" MT_FOUND_CONTRACTS="" MT_FOUND_BOUND="" MT_FOUND_SKELETONS=""
    if [[ ! -s $WORK/hop-A3.json ]]; then
        hop_fail N1 "no activation was created and read (walk 2), so there is no 'artifacts' relation to follow"
    elif follow N1 "$WORK/hop-A3.json" artifacts "the activation" kind=mission_type &&
        find_artifact N2 "$WORK/hop-N1.json" "the effective mission_type list of the activation" mission_type "$MT_ID" &&
        follow N3 "$WORK/item.json" effective-artifact "the $MT_ID effective item"; then
        walk_mt_body N "$WORK/hop-N3.json" "under the activation"
        mt_line activation "$ok" "$failed"
        local o=$HOP_OK f=$HOP_FAIL
        mt_graph_and_context
        MT_LINES+=("MISSION graph+context type=$MT_ID hops=$((HOP_OK - o))/$((HOP_OK - o + HOP_FAIL - f))")
        return
    fi
    mt_line activation "$ok" "$failed"
}

# ---------------------------------------------------------------------------------------------------------------
# --self-check: the pure parts of the client, no service needed

self_check() {
    local fails=0 total=0
    check() { # label actual expected
        total=$((total + 1))
        if [[ $2 == "$3" ]]; then
            printf '  [ ok ] %s\n' "$1"
        else
            printf '  [FAIL] %s: expected [%s], got [%s]\n' "$1" "$3" "$2"
            fails=$((fails + 1))
        fi
    }
    BASE=http://127.0.0.1:9
    # bash 5.2 reads an unquoted & of a replacement as "the match"; these checks run with that switched on
    shopt -s patsub_replacement 2>/dev/null || true
    echo "== self-check: expand"
    check "one filled variable" "$(expand '/x{?kind,state}' kind=a)" "/x?kind=a"
    check "two filled variables" "$(expand '/x{?kind,state}' kind=a state=b)" "/x?kind=a&state=b"
    check "two filled variables, other order of values" "$(expand '/x{?kind,state}' state=b kind=a)" "/x?kind=a&state=b"
    check "no variable filled" "$(expand '/x{?kind,state}')" "/x"
    check "path and two query variables" "$(expand '/api/v1/packs/{packId}/artifacts{?kind,state}' packId=built-in kind=agent_profile state=active)" \
        "/api/v1/packs/built-in/artifacts?kind=agent_profile&state=active"
    check "a & in a value is encoded, not replaced" "$(expand '/x{?kind,state}' kind='a&b' state='c d')" "/x?kind=a%26b&state=c%20d"
    check "a & in a path value" "$(expand '/p/{packId}/q' packId='a&b')" "/p/a%26b/q"
    check "text after the query template" "$(expand '/x{?kind,state}/y' kind=a state=b)" "/x?kind=a&state=b/y"
    check "an explode modifier expands as a plain variable" "$(expand '/x{?kind*}' kind=a)" "/x?kind=a"
    local out
    if out=$(expand '/x{&kind}' kind=a 2>/dev/null); then check "a {&..} continuation is refused" "filled" "failed"; else check "a {&..} continuation is refused" "failed" "failed"; fi
    if out=$(expand '/x{?kind;state}' kind=a 2>/dev/null); then check "an unsupported variable name is refused" "filled" "failed"; else check "an unsupported variable name is refused" "failed" "failed"; fi
    if out=$(expand '/x/{id}' kind=a 2>/dev/null); then check "an unfilled variable fails" "filled" "failed"; else check "an unfilled variable fails" "failed" "failed"; fi
    echo "== self-check: resolve"
    local href want
    for href in "/a/b" "$BASE/a/b"; do
        resolve "$href" && check "resolve $href" "$URL" "$BASE/a/b" || check "resolve $href" "refused: $REFUSAL" "$BASE/a/b"
    done
    for href in "http://evil.example/a" "https://127.0.0.1:9/a" "//evil.example/a" "a/b" "$BASE.evil/a"; do
        if resolve "$href"; then check "refuse $href" "followed as $URL" "refused"; else check "refuse $href" "refused" "refused"; fi
    done
    echo "== self-check: removal stays inside the temporary directory"
    mkdir "$WORK/inside"
    remove_inside "$WORK/inside" 2>/dev/null
    check "a path inside is removed" "$([[ -e $WORK/inside ]] && echo present || echo removed)" "removed"
    local outside
    outside=$(mktemp -d "${TMPDIR:-/tmp}/service-usability-run-check.XXXXXX") || return 1
    for href in "$outside" "$WORK/../$(basename "$outside")" "$WORK" "$WORK/" "/"; do
        remove_inside "$href" 2>/dev/null
        check "refuse to remove $href" "$?" "1"
    done
    check "the outside directory is still there" "$([[ -d $outside ]] && echo present || echo removed)" "present"
    rmdir "$outside"
    mkdir "$WORK/sibling-dir" && : >"$WORK/sibling-dir/kept"
    ln -s sibling-dir "$WORK/link"
    remove_inside "$WORK/link" 2>/dev/null
    check "a symlink inside is removed as a link" "$([[ -L $WORK/link ]] && echo present || echo removed)" "removed"
    check "the target of a removed symlink stays" "$([[ -e $WORK/sibling-dir/kept ]] && echo present || echo removed)" "present"
    echo "== self-check: option parsing"
    local rc err
    for href in --open-packs --profile --jar --attach --packs; do
        err=$("${BASH_SOURCE[0]}" "$href" 2>&1 >/dev/null)
        rc=$?
        check "$href without a value exits 2, for the reason" "$rc ${err%%$'\n'*}" "2 error: $href needs a value"
    done
    err=$("${BASH_SOURCE[0]}" --profile --keep 2>&1 >/dev/null)
    rc=$?
    check "--profile followed by an option exits 2, for the reason" "$rc ${err%%$'\n'*}" "2 error: --profile needs a value"
    echo "== self-check: mission type walk"
    check "words_minus keeps what the second list lacks" "$(words_minus "c a b" "b")" "a c"
    check "words_minus of equal sets is empty" "$(words_minus "$MT_CONTRACTS" "tasks review specify plan implement")" ""
    check "words_count of the steps SC-001 names" "$(words_count "$MT_SEQUENCE $MT_ON_DEMAND")" "13"
    check "words_count of an empty list" "$(words_count "")" "0"
    jq -n '{kind: "mission_type", missionType: {id: "t", steps: [{id: "a", onDemand: false}, {id: "b"}, {id: "c", onDemand: true}]}}' >"$WORK/mt-fixture.json"
    check "mt_steps lists each step in body order with its onDemand" "$(mt_steps "$WORK/mt-fixture.json" | tr '\n' ',')" "a false,b false,c true,"
    local before=$HOP_FAIL
    out=$(mt_expect X "fixture" "plan" "plan specify" step-contract)
    check "mt_expect names the relation and what it did not reach" "$out" \
        "  [FAIL] X    fixture: 1/2 reached; expected relation 'step-contract' not reached for: specify"
    out=$(mt_expect X "fixture" "specify plan" "plan specify" step-contract)
    check "mt_expect passes on the same set in another order" "$out" "  [ ok ] X    fixture: 2/2 reached"
    HOP_FAIL=$before
    echo "== self-check: charter-to-activation.py --self-test"
    python3 -I "$HERE/charter-to-activation.py" --self-test 2>&1 | sed 's/^/   /'
    check "charter-to-activation.py --self-test" "${PIPESTATUS[0]}" "0"
    say "RESULT SELF-CHECK $([[ $fails -eq 0 ]] && echo PASS || echo FAIL) checks=$((total - fails))/$total"
    [[ $fails -eq 0 ]]
}

identity_field() { # what is known of the service under test: its jar, or that it is unknown
    if [[ -n $ATTACH ]]; then printf ' service=unknown'; elif [[ -n $JAR_SHA ]]; then printf ' jar=%s' "${JAR_SHA:0:12}"; fi
}

summary() {
    say ""
    say "== summary"
    say "  client commit      : $COMMIT ($BRANCH)"
    if [[ -n $ATTACH ]]; then
        say "  service under test : unknown (--attach $BASE)"
    elif [[ -n $JAR_SHA ]]; then
        say "  service jar        : $JAR (sha256 $JAR_SHA)"
    fi
    say "  open-packs pin     : $PIN"
    say "  hops followed      : $HOP_OK ok, $HOP_FAIL failed"
    local f
    for f in "${HOPS_FAILED[@]:-}"; do [[ -n $f ]] && say "    failed: $f"; done
    say "  workarounds        : ${#WORKAROUNDS[@]}"
    for f in "${WORKAROUNDS[@]:-}"; do [[ -n $f ]] && say "    - $f"; done
    say "  unheld selections  : ${#DROPPED[@]}${DROPPED[*]:+ (${DROPPED[*]})}"
    local unlinked=0 k
    for k in "${!UNLINKED[@]}"; do unlinked=$((unlinked + UNLINKED[$k])); done
    say "  not linked (kind not served): $unlinked"
    while IFS= read -r k; do
        [[ -n $k ]] && say "    - $k x${UNLINKED[$k]}"
    done < <(printf '%s\n' "${!UNLINKED[@]}" | sort)
    say "  p95 pack-scoped    : $P95_PACK ms; under activation: $P95_ACT ms (limit $P95_LIMIT_MS ms, $READS reads each)"
    for f in "${MT_LINES[@]:-}"; do [[ -n $f ]] && say "$f"; done
    local verdict=PASS
    if [[ $HOP_FAIL -gt 0 ]]; then verdict=FAIL; fi
    keep_hint
    say "RESULT $verdict hops=$HOP_OK/$((HOP_OK + HOP_FAIL)) failed=$HOP_FAIL workarounds=${#WORKAROUNDS[@]} unheld=${#DROPPED[@]} unlinked=$unlinked p95=${P95_PACK}/${P95_ACT}ms client=${COMMIT:0:8}$([[ $COMMIT == *-dirty ]] && echo -dirty)$(identity_field) pin=$([[ $PIN =~ ^[0-9a-f]{40}$ ]] && echo "${PIN:0:8}" || echo none)"
    [[ $verdict == PASS ]]
}

main() {
    if [[ $SELF_CHECK -eq 1 ]]; then
        self_check
        return
    fi
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
        walk_mt_pack
        walk_mt_activation
    fi
    summary
}

main
