"""Draft the open-packs migration for charter #240 on a scratch copy of spec-kitty-open-packs.

Usage: python3 -I migrate.py <open-packs-copy> <report.json>

Removes the edge-authoring reference fields from artifact files (text edits, formatting kept),
appends graph edges for references no graph document holds on that unordered pair, and merges a
field `when` into an identical graph edge that has none. A directive's `procedures` is left alone (D1).

Afterwards, by hand in the open-packs copy: record `python3 scripts/ci/built_in_digest.py` as
tree_digest.value in packs/built-in.source.yaml, and regenerate the pack pages with
`CHARTER_CONTRACT_DIR=<charter at CHARTER_COMMIT>/contracts/packs python3 scripts/docs/gen_pack_reference.py`.
"""
import glob
import json
import os
import re
import sys

import yaml

ROOT = sys.argv[1]
OUT = sys.argv[2]
SUFFIX = {
    '.directive.yaml': 'directive', '.paradigm.yaml': 'paradigm', '.tactic.yaml': 'tactic',
    '.styleguide.yaml': 'styleguide', '.toolguide.yaml': 'toolguide', '.procedure.yaml': 'procedure',
    '.mission-type.yaml': 'mission_type',
}
SHARD = {'directive': 'directive', 'paradigm': 'paradigm', 'tactic': 'tactic', 'styleguide': 'styleguide',
         'toolguide': 'toolguide', 'procedure': 'procedure', 'mission_type': 'mission_type', 'action': 'action'}
# relation a field reference becomes when no graph edge holds the pair (the interim mapping for minting
# fields; the dominant graph relation of the source kind for the fields that never minted)
DEFAULT_REL = {'directive': 'suggests', 'paradigm': 'suggests', 'tactic': 'suggests', 'procedure': 'suggests',
               'styleguide': 'suggests', 'toolguide': 'suggests', 'mission_type': 'scope', 'action': 'scope'}

packs = sorted(p for p in os.listdir(os.path.join(ROOT, 'packs')) if os.path.isdir(os.path.join(ROOT, 'packs', p)))


def norm(type_, id_):
    if '::' in id_:
        pack, local = id_.split('::', 1)
        return f'{pack}::{type_}:{local}'
    return f'{type_}:{id_}'


def path_urn(ref):
    """A pack path of an artifact file -> its URN, else None (a citation)."""
    p = ref.strip()
    if not p.startswith('packs/'):
        return None
    full = os.path.join(ROOT, p)
    kind = next((k for s, k in SUFFIX.items() if p.endswith(s)), None)
    if not kind or not os.path.isfile(full):
        return None
    doc = yaml.safe_load(open(full)) or {}
    pack_of = p.split('/')[1]
    return pack_of, f"{kind}:{doc.get('id')}"


report = {'part_b_when': [], 'reason_merged': [], 'edges_added': [], 'when_merged': [], 'subsumed_reverse': [], 'relation_differs': [],
          'citations_dropped': [], 'when_differs': [], 'files_edited': [], 'unresolved': []}

for pack in packs:
    pdir = os.path.join(ROOT, 'packs', pack)
    docs = sorted(glob.glob(pdir + '/*.graph.yaml')) + sorted(glob.glob(pdir + '/drg/fragment.yaml'))
    graph = []  # (doc, edge)
    for d in docs:
        for e in (yaml.safe_load(open(d)) or {}).get('edges') or []:
            graph.append((d, e))
    pairs = {}
    for d, e in graph:
        pairs.setdefault(frozenset((e['source'], e['target'])), []).append((d, e))
    wanted = []  # (source, target, rel, when, file, field)

    for path in sorted(glob.glob(pdir + '/**/*.yaml', recursive=True)):
        kind = next((k for s, k in SUFFIX.items() if path.endswith(s)), None)
        if not kind:
            continue
        doc = yaml.safe_load(open(path))
        if not isinstance(doc, dict):
            continue
        rel = os.path.relpath(path, ROOT)
        src = f"{kind}:{doc.get('id')}"
        touched = False

        def typed(refs, source, field, rel_default):
            for r in refs or []:
                if isinstance(r, dict) and r.get('type') and r.get('id'):
                    wanted.append((source, norm(r['type'], str(r['id'])), rel_default, (r.get('when'), r.get('reason')), rel, field))
                else:
                    report['unresolved'].append({'file': rel, 'field': field, 'value': r})

        if kind in ('directive', 'paradigm', 'tactic', 'procedure', 'mission_type') and 'references' in doc:
            typed(doc['references'], src, 'references', DEFAULT_REL[kind])
            touched = True
        if kind == 'paradigm' and 'directive_refs' in doc:
            for d_ in doc['directive_refs'] or []:
                wanted.append((src, f'directive:{d_}', 'requires', (None, None), rel, 'directive_refs'))
            touched = True
        if kind in ('tactic', 'mission_type'):
            for step in doc.get('steps') or []:
                if isinstance(step, dict) and 'references' in step:
                    source = src if kind == 'tactic' else f"action:{doc.get('id')}/{step.get('id')}"
                    typed(step['references'], source, 'steps[].references',
                          DEFAULT_REL['tactic' if kind == 'tactic' else 'action'])
                    touched = True
        if kind in ('styleguide', 'toolguide') and 'references' in doc:
            for r in doc['references'] or []:
                hit = path_urn(str(r))
                if hit is None:
                    report['citations_dropped'].append({'file': rel, 'citation': r})
                else:
                    tpack, urn = hit
                    tgt = urn if tpack == pack else f'{tpack}::{urn}'
                    wanted.append((src, tgt, DEFAULT_REL[kind], (None, None), rel, 'references'))
            touched = True
        if touched:
            report['files_edited'].append(rel)

    additions = {}
    for source, target, relation, meta, file, field in wanted:
        when, reason = meta
        in_fragment = pack != 'built-in'
        hits = pairs.get(frozenset((source, target)), [])
        if not hits:
            key = (source, target)
            if key in additions:
                continue
            target_doc = (os.path.join(pdir, 'drg/fragment.yaml') if pack != 'built-in'
                          else os.path.join(pdir, SHARD[source.split(':')[0]] + '.graph.yaml'))
            if when and in_fragment:  # the org-fragment edge has no `when` until the charter adds it (part B)
                report['part_b_when'].append({'pack': pack, 'doc': os.path.relpath(target_doc, ROOT),
                                              'source': source, 'target': target, 'when': when})
            additions[key] = (target_doc, source, target, relation, when)
            report['edges_added'].append({'pack': pack, 'doc': os.path.relpath(target_doc, ROOT), 'source': source,
                                          'target': target, 'relation': relation,
                                          'when': None if in_fragment else when, 'reason': reason,
                                          'from': f'{file} {field}'})
            continue
        same_dir = [(d, e) for d, e in hits if e['source'] == source]
        if not same_dir:
            report['subsumed_reverse'].append({'pack': pack, 'field_ref': f'{source} -> {target}', 'from': file,
                                               'graph': [f"{e['source']} -{e['relation']}-> {e['target']}"
                                                         for _, e in hits]})
            continue
        d, e = same_dir[0]
        if e['relation'] != relation:
            report['relation_differs'].append({'pack': pack, 'ref': f'{source} -> {target}', 'field': relation,
                                               'graph': e['relation']})
        if reason:
            if not e.get('reason'):
                report['reason_merged'].append({'pack': pack, 'doc': os.path.relpath(d, ROOT), 'source': source,
                                                'target': target, 'key': 'reason', 'value': reason})
                e['reason'] = reason
            elif e['reason'] != reason:
                report['when_differs'].append({'pack': pack, 'source': source, 'target': target,
                                               'graph_reason': e['reason'], 'field_reason': reason})
        if when and in_fragment and not e.get('when'):
            report['part_b_when'].append({'pack': pack, 'doc': os.path.relpath(d, ROOT), 'source': source,
                                          'target': target, 'when': when})
        elif when:
            # another edge on the pair, in this direction, may already say it (a pair holding two relations)
            if any(o.get('when') == when for _, o in same_dir):
                continue
            if not e.get('when'):
                if e['relation'] == relation or True:
                    report['when_merged'].append({'pack': pack, 'doc': os.path.relpath(d, ROOT), 'source': source,
                                                  'target': target, 'key': 'when', 'value': when})
                    e['when'] = when  # avoid merging twice
            elif e['when'] != when:
                report['when_differs'].append({'pack': pack, 'source': source, 'target': target,
                                               'graph_when': e['when'], 'field_when': when})

json.dump(report, open(OUT, 'w'), indent=1, default=str)


# ---- apply text edits -------------------------------------------------------------------------------------------

def drop_block(lines, key_re, indent):
    """Drop every line `<indent>key:` and the lines below it indented deeper (or list items at the same indent)."""
    out, i = [], 0
    pat = re.compile(r'^' + indent + key_re + r':')
    while i < len(lines):
        if pat.match(lines[i]):
            i += 1
            while i < len(lines):
                ln = lines[i]
                if ln.strip() == '':
                    i += 1
                    continue
                lead = len(ln) - len(ln.lstrip(' '))
                if lead > len(indent) or (lead == len(indent) and ln.lstrip().startswith('- ')):
                    i += 1
                    continue
                break
            continue
        out.append(lines[i])
        i += 1
    return out


for rel in report['files_edited']:
    full = os.path.join(ROOT, rel)
    original = open(full).read()
    lines = original.rstrip('\n').split('\n')
    kind = next(k for s, k in SUFFIX.items() if rel.endswith(s))
    lines = drop_block(lines, 'references', '')
    if kind == 'paradigm':
        lines = drop_block(lines, 'directive_refs', '')
    if kind in ('tactic', 'mission_type'):
        for ind in ('  ', '    '):
            lines = drop_block(lines, 'references', ind)
    while lines and lines[-1].strip() == '':
        lines.pop()
    open(full, 'w').write('\n'.join(lines) + ('\n' if original.endswith('\n') else ''))
    yaml.safe_load(open(full))  # still parses


def q(s):
    return json.dumps(s, ensure_ascii=False)


by_doc = {}
for a in report['edges_added']:
    by_doc.setdefault(os.path.join(ROOT, a['doc']), []).append(a)
for doc_path, adds in by_doc.items():
    text = open(doc_path).read()
    lines = text.rstrip('\n').split('\n')
    last_src = next(ln for ln in reversed(lines) if ln.lstrip().startswith('- source:'))
    ind = last_src[:len(last_src) - len(last_src.lstrip())]
    quoted = '"' in last_src
    fmt = (lambda v: q(v)) if quoted else (lambda v: v)
    block = []
    for a in adds:
        block.append(f"{ind}- source: {fmt(a['source'])}")
        block.append(f"{ind}  target: {fmt(a['target'])}")
        block.append(f"{ind}  relation: {a['relation']}")
        if a['when']:
            block.append(f"{ind}  when: {q(a['when'])}")
        if a['reason']:
            block.append(f"{ind}  reason: {q(a['reason'])}")
    open(doc_path, 'w').write('\n'.join(lines + block) + '\n')

for m in report['when_merged'] + report['reason_merged']:
    path = os.path.join(ROOT, m['doc'])
    lines = open(path).read().split('\n')
    for i, ln in enumerate(lines):
        if ln.lstrip().startswith('- source:') and ln.split('source:', 1)[1].strip().strip('"') == m['source']:
            if lines[i + 1].split('target:', 1)[1].strip().strip('"') == m['target']:
                ind = ln[:len(ln) - len(ln.lstrip())] + '  '
                j = i + 1
                while j < len(lines) and lines[j].startswith(ind) and not lines[j].lstrip().startswith('- '):
                    j += 1
                lines.insert(j, f"{ind}{m['key']}: {q(m['value'])}")
                break
    else:
        raise SystemExit(f'edge not found for when merge: {m}')
    open(path, 'w').write('\n'.join(lines))

for d in set(by_doc) | {os.path.join(ROOT, m['doc']) for m in report['when_merged'] + report['reason_merged']}:
    yaml.safe_load(open(d))


# ---- restamp every pack manifest (ADR-0042 recipe, PackManifests.java) -------------------------------------------
import hashlib


def content_hash(path):
    data = open(path, 'rb').read().replace(b'\r\n', b'\n').replace(b'\r', b'\n')
    return hashlib.sha256(data).hexdigest()


for manifest in sorted(glob.glob(os.path.join(ROOT, 'packs', '*', 'pack-manifest.yaml'))):
    base = os.path.dirname(manifest)
    doc = yaml.safe_load(open(manifest))
    text = open(manifest).read()
    for section in ('constituents', 'presets'):
        for c in doc.get(section) or []:
            new = content_hash(os.path.join(base, c['path']))
            if new != c['content_hash']:
                text = text.replace(c['content_hash'], new, 1)
                c['content_hash'] = new
    obj = {'schema_version': doc['schema_version'],
           'constituents': [{k: c[k] for k in ('kind', 'id', 'path', 'content_hash')} for c in doc['constituents']]}
    if doc.get('presets'):
        obj['presets'] = [{k: p[k] for k in ('id', 'path', 'content_hash')} for p in doc['presets']]
    canon = json.dumps(obj, sort_keys=True, separators=(',', ':'), ensure_ascii=False)
    mh = hashlib.sha256(canon.encode('utf-8')).hexdigest()
    text = text.replace(doc['manifest_hash'], mh, 1)
    open(manifest, 'w').write(text)
    report.setdefault('manifests_restamped', []).append(os.path.relpath(manifest, ROOT))

json.dump(report, open(OUT, 'w'), indent=1, default=str)
print({k: len(v) for k, v in report.items()})
