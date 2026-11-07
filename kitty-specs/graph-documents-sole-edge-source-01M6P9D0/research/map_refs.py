"""Map every reference dropped from an open-packs artifact (charter #240) to what replaces it.

Usage: python3 -I map_refs.py <packs before (dad323f)> <packs after (part A + A2)> <a2 map.json> <out.md>

Each reference resolves to exactly one of:
  live edge     - the after graph documents hold an edge on the pair, in the same direction
  live reverse  - they hold the pair only in the other direction (ADR-0027: one edge per pair)
  added         - that edge was not in the before graph documents; the migration added it
  text          - a citation that names no artifact; it now stands in the artifact's own text
A reference with none of these is reported as UNRESOLVED (the run fails).
"""
import collections
import glob
import json
import os
import sys

import yaml

BEFORE, AFTER, A2MAP, OUT = sys.argv[1:5]
SUFFIX = {'.directive.yaml': 'directive', '.paradigm.yaml': 'paradigm', '.tactic.yaml': 'tactic',
          '.styleguide.yaml': 'styleguide', '.toolguide.yaml': 'toolguide', '.procedure.yaml': 'procedure',
          '.mission-type.yaml': 'mission_type', '.agent.yaml': 'agent_profile'}
# Citations that name an artifact by something other than its file path.
NAMED = {
    'See also, for the security- and performance-specific treatment: Drupal Security and Performance Styleguide.':
        'styleguide:drupal-security-performance',
    'See also, for the general coding-convention treatment this file cross-references: Drupal Conventions Styleguide.':
        'styleguide:drupal-conventions',
    'packs/built-in/assets/docs_structural_lint.config.yaml': 'asset:common-docs-structural-lint',
}


def edges(root):
    out = collections.defaultdict(list)
    for pack in sorted(os.listdir(os.path.join(root, 'packs'))):
        pdir = os.path.join(root, 'packs', pack)
        if not os.path.isdir(pdir):
            continue
        for d in sorted(glob.glob(pdir + '/*.graph.yaml')) + glob.glob(pdir + '/drg/fragment.yaml'):
            for e in (yaml.safe_load(open(d)) or {}).get('edges') or []:
                out[pack].append((e['source'], e['target'], e['relation'], os.path.relpath(d, root)))
    return out


def norm(type_, id_):
    if '::' in str(id_):
        pack, local = str(id_).split('::', 1)
        return f'{pack}::{type_}:{local}'
    return f'{type_}:{id_}'


def path_urn(root, ref):
    p = str(ref).strip()
    kind = next((k for s, k in SUFFIX.items() if p.endswith(s)), None)
    full = os.path.join(root, p)
    if not p.startswith('packs/') or not kind or not os.path.isfile(full):
        return None
    doc = yaml.safe_load(open(full)) or {}
    urn = f"{kind}:{doc.get('profile-id') if kind == 'agent_profile' else doc.get('id')}"
    return p.split('/')[1], urn


before, after = edges(BEFORE), edges(AFTER)
a2 = json.load(open(A2MAP))['covered']
rows, unresolved = [], []


def text_home(pack, file_rel, needle):
    full = os.path.join(AFTER, file_rel)
    if os.path.isfile(full) and needle in open(full).read():
        return file_rel
    guide = full.replace('.toolguide.yaml', '.guide.md')
    if os.path.isfile(guide) and ('amazeeio/drupal-agents-md' in needle and 'drupal-agents-md' in open(guide).read()):
        return os.path.relpath(guide, AFTER) + ' (already cited in its References)'
    return None


def resolve(pack, source, target, file_rel, field):
    pair = {source, target}
    for s, t, r, d in after[pack]:
        if {s, t} == pair:
            was = any({bs, bt} == pair for bs, bt, _, _ in before[pack])
            how = 'live edge' if s == source else 'live reverse'
            if not was:
                how = 'added'
            rows.append((pack, file_rel, field, f'{source} → {target}', how, f'`{s} -{r}-> {t}` in `{d}`'))
            return
    unresolved.append((pack, file_rel, field, f'{source} → {target}'))


for pack in sorted(os.listdir(os.path.join(BEFORE, 'packs'))):
    pdir = os.path.join(BEFORE, 'packs', pack)
    if not os.path.isdir(pdir):
        continue
    for path in sorted(glob.glob(pdir + '/**/*.yaml', recursive=True)):
        kind = next((k for s, k in SUFFIX.items() if path.endswith(s)), None)
        if not kind or kind == 'agent_profile':
            continue
        doc = yaml.safe_load(open(path))
        if not isinstance(doc, dict):
            continue
        rel = os.path.relpath(path, BEFORE)
        src = f"{kind}:{doc.get('id')}"
        if kind in ('directive', 'paradigm', 'tactic', 'procedure', 'mission_type'):
            for r in doc.get('references') or []:
                resolve(pack, src, norm(r['type'], r['id']), rel, 'references')
        if kind in ('tactic', 'mission_type'):
            for step in doc.get('steps') or []:
                if isinstance(step, dict):
                    s = src if kind == 'tactic' else f"action:{doc.get('id')}/{step.get('id')}"
                    for r in step.get('references') or []:
                        resolve(pack, s, norm(r['type'], r['id']), rel, 'steps[].references')
        if kind == 'paradigm':
            for d_ in doc.get('directive_refs') or []:
                resolve(pack, src, f'directive:{d_}', rel, 'directive_refs')
        if kind == 'directive':
            for i, _ in enumerate(doc.get('procedures') or [], 1):
                for pid in a2[f"{doc.get('id')}#{i}"]:
                    resolve(pack, src, f'procedure:{pid}', rel, f'procedures[{i}]')
        if kind in ('styleguide', 'toolguide'):
            for r in doc.get('references') or []:
                hit = path_urn(BEFORE, r)
                if hit or r in NAMED:
                    tpack, urn = hit if hit else (pack, NAMED[r])
                    resolve(pack, src, urn if tpack == pack else f'{tpack}::{urn}', rel, 'references')
                    continue
                needle = str(r)
                for key in ('https://', 'http://'):
                    if key in needle:
                        needle = needle[needle.index(key):].split()[0].rstrip('.,;)')
                home = text_home(pack, rel, needle)
                if home:
                    rows.append((pack, rel, 'references', f'citation `{str(r)[:80]}`', 'text', f'`{home}`'))
                else:
                    unresolved.append((pack, rel, 'references', str(r)))

count = collections.Counter(r[4] for r in rows)
with open(OUT, 'w') as f:
    f.write('# Every dropped reference and what replaces it (charter #240)\n\n')
    f.write('Generated by `map_refs.py` from open-packs `dad323f` (before) and part A + A2 (after).\n\n')
    f.write('| Outcome | Count |\n|---|---|\n')
    for k in ('live edge', 'live reverse', 'added', 'text'):
        f.write(f'| {k} | {count[k]} |\n')
    f.write(f'| unresolved | {len(unresolved)} |\n\n')
    f.write('| Pack | File | Field | Reference | Outcome | Replaced by |\n|---|---|---|---|---|---|\n')
    for row in rows:
        f.write('| ' + ' | '.join(str(c).replace('|', '\\|') for c in row) + ' |\n')
print(dict(count), 'unresolved', len(unresolved))
for u in unresolved:
    print('UNRESOLVED', u)
sys.exit(1 if unresolved else 0)
