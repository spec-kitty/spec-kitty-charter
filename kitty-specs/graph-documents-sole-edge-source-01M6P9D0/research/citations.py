"""Second step of the open-packs part A draft for charter #240 (operator ruling Q3, option C).

Usage: python3 -I citations.py <open-packs-copy>   (after migrate.py)

A styleguide citation that names no pack artifact moves into the styleguide's own text: inline as
"(source: ...)" on the principle it backs, or into a closing "Sources: ..." sentence on the last
principle when it backs the whole guide. A citation that names a pack artifact without a live edge
becomes a graph edge. Edits are text edits on one-line double-quoted principles, so layout stays.
"""
import os
import sys

ROOT = sys.argv[1]
SG = 'packs/{}/styleguides/{}.styleguide.yaml'
DIVIO = 'packs/built-in/missions/documentation/templates/divio/'

# (file, unique substring of the principle line, text appended inside its closing quote)
INLINE = [
    (SG.format('built-in', 'deployable-skill-authoring'), 'Use stable, project-owned terminology',
     ' (source: docs/host-surface-parity.md; docs/adr/3.x/2026-04-14-2-agent-skills-renderer-for-codex-and-vibe.md)'),
    (SG.format('built-in', 'divio-type-discipline'), '"Tutorials are learning-oriented', f' (source: {DIVIO}tutorial-template.md)'),
    (SG.format('built-in', 'divio-type-discipline'), '"How-tos are task-oriented', f' (source: {DIVIO}howto-template.md)'),
    (SG.format('built-in', 'divio-type-discipline'), '"Reference is information-oriented', f' (source: {DIVIO}reference-template.md)'),
    (SG.format('built-in', 'divio-type-discipline'), '"Explanation is understanding-oriented', f' (source: {DIVIO}explanation-template.md)'),
    (SG.format('built-in', 'drupal-security-performance'), '"Access-check every entity query',
     ' (source: Drupal.org change record https://www.drupal.org/node/3201242, which this entry follows rather than'
     ' the amazee.io source: deprecated in 9.2.0, an error from 10.0.0)'),
]
# (file, sources for the whole guide) -> appended to the last principle as " Sources: a; b."
WHOLE = [
    (SG.format('built-in', 'drupal-conventions'),
     ['amazee.io drupal-agents-md, Vanilla variant (https://github.com/amazeeio/drupal-agents-md/blob/main/Vanilla/AGENTS.md)',
      'Drupal coding standards (https://www.drupal.org/docs/develop/standards)']),
    (SG.format('built-in', 'drupal-security-performance'),
     ['amazee.io drupal-agents-md, Vanilla variant: Security & Performance Guidelines, Caching Strategies, Anti-Patterns'
      ' #3, #6, #7, #10, #11 (https://github.com/amazeeio/drupal-agents-md/blob/main/Vanilla/AGENTS.md)']),
    (SG.format('built-in', 'planning-and-tracking'), ['.kittify/glossaries/planning-and-tracking.yaml']),
    (SG.format('built-in', 'test-desiderata-and-boundaries'),
     ['https://testdesiderata.com/', 'https://patterns.sddevelopment.be/practices/define_test_boundaries/']),
    ('packs/built-in/styleguides/writing/kitty-glossary-writing.styleguide.yaml', ['glossary/README.md', 'docs/context/']),
    (SG.format('cave-kitty', 'cave-sty-001-communication'), ['https://github.com/JuliusBrussee/caveman']),
    (SG.format('internal', 'executive-debrief'), ['docs/development/reporting/debrief-styleguide.md']),
    (SG.format('internal', 'feature-and-scenario-authoring'),
     ['tests/scenarios/README.md', 'docs/development/testing/acceptance-scenarios.md']),
]
# "See also" between the two Drupal styleguides: an artifact reference with no edge on the pair.
EDGE = ('packs/built-in/styleguide.graph.yaml', """- source: styleguide:drupal-conventions
  target: styleguide:drupal-security-performance
  relation: suggests
  reason: Each guide named the other as its companion ("See also"); this one is the security- and performance-specific treatment.
""")


def lines_of(path):
    with open(os.path.join(ROOT, path)) as f:
        return f.read().split('\n')


def save(path, lines):
    with open(os.path.join(ROOT, path), 'w') as f:
        f.write('\n'.join(lines))


def principle_bounds(lines):
    start = lines.index('principles:')
    end = next(i for i in range(start + 1, len(lines)) if lines[i] and not lines[i].startswith((' ', '#')))
    items = [i for i in range(start + 1, end) if lines[i].startswith('  - "')]
    return items


def append_inside(line, text):
    assert line.rstrip().endswith('"'), line
    body = line.rstrip()[:-1]
    text = text.replace('"', '\\"')
    if text.startswith(' Sources'):
        if not body.endswith(('.', '!', '?')):
            body += '.'
        return body + text + '"'
    if body.endswith('.'):  # an inline source goes inside the sentence's full stop
        return body[:-1] + text + '."'
    return body + text + '"'


for path, marker, text in INLINE:
    lines = lines_of(path)
    hits = [i for i in principle_bounds(lines) if marker in lines[i]]
    assert len(hits) == 1, (path, marker, hits)
    lines[hits[0]] = append_inside(lines[hits[0]], text)
    save(path, lines)

for path, sources in WHOLE:
    lines = lines_of(path)
    last = principle_bounds(lines)[-1]
    lines[last] = append_inside(lines[last], ' Sources: ' + '; '.join(sources) + '.')
    save(path, lines)

path, block = EDGE
full = os.path.join(ROOT, path)
text = open(full).read()
anchor = "- source: styleguide:drupal-conventions\n  target: toolguide:drupal-review-checks\n  relation: suggests\n"
assert text.count(anchor) == 1
open(full, 'w').write(text.replace(anchor, anchor + block, 1))
print('citations placed:', len(INLINE), 'inline,', len(WHOLE), 'guides with Sources; 1 edge added')
