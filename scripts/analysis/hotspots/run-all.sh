#!/usr/bin/env bash
# Hotspots and churn of the 2026-11-20 codebase assessment, reproducible and read-only on the repository.
# Run from the repository root with full history:  bash scripts/analysis/hotspots/run-all.sh
# Other commits: ANALYSIS_REV=<pin> ANALYSIS_BASE=<trend baseline> ANALYSIS_OUT=<dir> bash scripts/analysis/hotspots/run-all.sh
# Needs git, python3 and uvx (lizard 1.17.13 is fetched by uvx). Output: build/analysis/hotspots/tables.md
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REV=${ANALYSIS_REV:-25e24c372bae838d95a9328be57ee2063f6c05d1}
BASE=${ANALYSIS_BASE:-fd20faacb}
export ANALYSIS_REV="$REV" ANALYSIS_BASE="$BASE"
export ANALYSIS_OUT="${ANALYSIS_OUT:-$PWD/build/analysis/hotspots}"
mkdir -p "$ANALYSIS_OUT"
# 1. churn: git log REV --no-merges -M --numstat, renames resolved, bookkeeping flagged
python3 -I "$HERE/churn.py" "$REV"
# 2. complexity at REV and at the 2026-11-12 baseline, from git archive extractions (no checkout)
for r in "$REV" "$BASE"; do
  short=${r:0:8}
  d="$ANALYSIS_OUT/src-$short"
  if [ ! -d "$d" ]; then mkdir -p "$d"; git archive "$r" service client-java | tar -x -C "$d"; fi
  uvx -q --from lizard==1.17.13 python -I "$HERE/complexity.py" "$d" "$ANALYSIS_OUT/cx-$short.json"
done
# 3. hotspots, coupling, trend, streams and sizes
python3 -I "$HERE/analyze.py" > /dev/null
echo "tables: $ANALYSIS_OUT/tables.md"
