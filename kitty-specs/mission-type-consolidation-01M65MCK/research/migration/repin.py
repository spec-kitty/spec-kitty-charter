"""Apply the WP06 re-pin edits to a charter checkout: pin, re-vendor qa, file-kinds and expected-red."""
import os, re, shutil, sys
repo, upstream, sha = sys.argv[1], sys.argv[2], sys.argv[3]
def rw(p, f):
    s = open(p, encoding="utf-8").read(); n = f(s); assert n != s, p; open(p, "w", encoding="utf-8").write(n)
rw(f"{repo}/gradle.properties", lambda s: re.sub(r"openPacksRef=[0-9a-f]{40}", f"openPacksRef={sha}", s))
# re-vendor packs/qa (PROVENANCE.md and README.md are local)
local = {"PROVENANCE.md", "README.md"}
v, u = f"{repo}/packs/qa", f"{upstream}/packs/qa"
def files(root):
    out = set()
    for d, _, fs in os.walk(root):
        for f in fs:
            out.add(os.path.relpath(os.path.join(d, f), root))
    return out
for f in files(v) - files(u) - local:
    os.remove(os.path.join(v, f))
for d, ds, fs in os.walk(v, topdown=False):
    if not os.listdir(d): os.rmdir(d)
for f in files(u) - local:
    os.makedirs(os.path.dirname(os.path.join(v, f)), exist_ok=True); shutil.copy2(os.path.join(u, f), os.path.join(v, f))
# file-kinds.yaml: the seven #194 exclusions become retired rules; missions READMEs are excluded
def fk(s):
    a = s.index('  - match: "**/mission-steps/**/step.yaml"')
    b = s.index('  - match: "built-in.source.yaml"')
    s = s[:a] + s[b:]
    s = s.replace("\n# Files with no schema (#193, ADR-0040)", '''
# The retired mission tier (#194, ADR-0044 decision 6): files of these families are refused by name, in the load and in
# validate mode, so a stale one cannot hide. Removed when the service is incorporated into core and no supported pack
# holds the old layout.
retired:
  - match: "**/mission_types/*.yaml"
    reason: the mission-type file moved to missions/<id>/<id>.mission-type.yaml (ADR-0044)
  - match: "**/missions/*/mission.yaml"
    reason: folded into the mission-type file (ADR-0044)
  - match: "**/missions/*/mission-runtime.yaml"
    reason: folded into the mission-type file (ADR-0044)
  - match: "**/governance-profile.yaml"
    reason: folded into the mission-type file (ADR-0044)
  - match: "**/missions/*/expected-artifacts.yaml"
    reason: folded into the mission-type steps (ADR-0044)
  - match: "**/actions/*/index.yaml"
    reason: folded into the mission-type steps' references (ADR-0044)
  - match: "**/mission-steps/**/*"
    reason: folded into the mission-type steps and their companions (ADR-0044)
  - match: "**/missions/*/command-templates/*"
    reason: folded into the mission-type steps' prompts (ADR-0044)

# Files with no schema (#193, ADR-0040)''', 1)
    return s
rw(f"{repo}/contracts/packs/file-kinds.yaml", fk)
def er(s):
    # #194's own entries go: other:missions is green at the new pin, and the empty mission-type kind has files.
    a = s.index("  - kind: other:missions\n")
    b = s.index("  - kind: ", a + 5)
    s = s[:a] + s[b:]
    a = s.index("  - kind: mission-type\n")
    b = s.find("\n  - ", a + 5)
    return s[:a] + (s[b + 1:] if b > 0 else "")
rw(f"{repo}/service/packs/src/test/resources/whole-pack/expected-red.yaml", er)
print("re-pin edits applied")
