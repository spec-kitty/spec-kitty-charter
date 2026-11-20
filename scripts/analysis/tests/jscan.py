#!/usr/bin/env python3
"""Java port (approximate) of test-quality-scan.py flags. Read-only; prints JSON summary."""
import re, sys, json, pathlib, collections

ROOT = pathlib.Path(sys.argv[1])
OUT = pathlib.Path(sys.argv[2])
ANN = re.compile(r'^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|Property|Example)\b')
SIG = re.compile(r'^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+)?(?:[\w<>\[\],? ]+\s+)(\w+)\s*\(')
ASSERT = re.compile(r'\bassert\w*\s*\(|\bassertThat\w*\s*\(|\bassertThrows\w*\s*\(|\bverify\w*\s*\(|\bfail\s*\(|\.andExpect\s*\(|\bexpect\w*\s*\(|\bcheck\w*\s*\(|\bassertSoftly|\bcatchThrowable')
WEAK = re.compile(r'\.(isNotNull|isTrue|isNotEmpty)\(\)')
STRONG_CHAIN = re.compile(r'\.(isEqualTo|containsExactly|hasMessage\w*|contains\w*|extracting|satisfies|hasSize|isEmpty|isFalse|isSameAs|startsWith|endsWith|matches|isBetween|isGreater\w*|isLess\w*|isZero|isPositive|isNegative|hasFieldOrProperty\w*|returns|isEqualToComparingFieldByField|usingRecursiveComparison|hasValue|containsEntry|isPresent|isEmpty|hasCauseInstanceOf|hasRootCause\w*|isNull|isNotEqualTo|doesNotContain\w*|allSatisfy|anySatisfy|noneMatch|allMatch|hasStatus|isIn|isNotIn|asString|isInstanceOfSatisfying|hasToString)\(')
TYPEONLY = re.compile(r'assertThrows\s*\(|isInstanceOf\s*\(|isThrownBy|isExactlyInstanceOf')
SLEEP = re.compile(r'Thread\.sleep|TimeUnit\.\w+\.sleep|\.sleep\(|LockSupport\.park')
WALL = re.compile(r'System\.currentTimeMillis|System\.nanoTime|Instant\.now\(\)|Clock\.systemUTC|LocalDateTime\.now')
AWAIT = re.compile(r'Awaitility|await\(\)|\.atMost\(|\.until\(')
SKIP = re.compile(r'assume\w*\(|Assumptions\.|@Disabled|@EnabledOn\w*|@DisabledOn\w*|@EnabledIf\w*|@DisabledIf\w*')
MOCK = re.compile(r'\bmock\s*\(|@Mock\b|Mockito\.mock|spy\s*\(')
VERIFY = re.compile(r'\bverify\s*\(')

def methods(lines):
    i = 0
    while i < len(lines):
        if ANN.match(lines[i]):
            ann_line = i
            j = i
            while j < len(lines) and not SIG.match(lines[j]) or (j < len(lines) and lines[j].strip().startswith('@')):
                j += 1
                if j - i > 15: break
            if j >= len(lines): break
            m = SIG.match(lines[j])
            if not m: i += 1; continue
            name = m.group(1)
            depth = 0; started = False; k = j
            while k < len(lines):
                for ch in lines[k]:
                    if ch == '{': depth += 1; started = True
                    elif ch == '}': depth -= 1
                if started and depth == 0: break
                k += 1
            yield ann_line, j, k, name, lines[ann_line:j], lines[j:k + 1]
            i = k + 1
        else:
            i += 1

def module_of(p):
    parts = p.parts
    return parts[parts.index('service') + 1] if 'service' in parts else 'other'

rows = []; per_mod = collections.defaultdict(collections.Counter)
style = collections.defaultdict(collections.Counter)
for f in sorted(ROOT.glob('service/*/src/**/*.java')):
    if '/src/main/' in str(f): continue
    txt = f.read_text(encoding='utf-8', errors='replace')
    lines = txt.splitlines()
    mod = module_of(f.relative_to(ROOT))
    style[mod]['files'] += 1
    style[mod]['displayname'] += txt.count('@DisplayName')
    style[mod]['gwt_comments'] += len(re.findall(r'//\s*(given|when|then|arrange|act|assert)\b', txt, re.I))
    style[mod]['nested'] += txt.count('@Nested')
    style[mod]['assertj'] += txt.count('assertThat')
    style[mod]['junit_assert'] += len(re.findall(r'\bassert(Equals|True|False|NotNull|Null|Same)\(', txt))
    for a, s, e, name, anns, body in methods(lines):
        b = '\n'.join(body[1:])
        flags = []
        n_assert = len(ASSERT.findall(b))
        if n_assert == 0 and 'assertDoesNotThrow' not in b and 'Throwing' not in b:
            # helper call that may assert: calls a local lower-case method; count as possible
            flags.append('no-assertion')
        if n_assert and WEAK.search(b) and not STRONG_CHAIN.search(b) and not TYPEONLY.search(b):
            flags.append('weak-only-assert')
        if TYPEONLY.search(b) and not re.search(r'hasMessage|getMessage|\.code\(\)|\.reason|extracting|satisfies|isEqualTo|contains|returns|hasFieldOrProperty|getCode|status', b):
            flags.append('type-only-assert')
        if len(MOCK.findall(b)) >= 4: flags.append('over-mocking')
        if VERIFY.search(b): flags.append('interaction-assert')
        if e - s > 80: flags.append('long-test')
        if n_assert >= 15: flags.append('many-asserts')
        if SLEEP.search(b): flags.append('sleep')
        if WALL.search(b): flags.append('wallclock')
        if SKIP.search(b) or any(SKIP.search(x) for x in anns): flags.append('skip-or-xfail')
        style[mod]['methods'] += 1
        words = len(re.findall(r'[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+', name))
        if words >= 5: style[mod]['sentence_names'] += 1
        if name.startswith('test') or re.match(r'^\w+_\w+$', name) and words < 4: style[mod]['terse_names'] += 1
        if flags:
            per_mod[mod]['flagged'] += 1
            for fl in flags: per_mod[mod][fl] += 1
            rows.append({'file': str(f.relative_to(ROOT)), 'line': s + 1, 'name': name, 'flags': flags})

OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'tests.json').write_text(json.dumps(rows, indent=1))
tot = collections.Counter(); totm = 0
for m in style: totm += style[m]['methods']
for m in per_mod: tot.update(per_mod[m])
summary = {'methods': totm, 'flagged': tot['flagged'], 'flags': {k: v for k, v in tot.items() if k != 'flagged'},
           'per_module': {m: {'methods': style[m]['methods'], **per_mod[m], **{('s_' + k): v for k, v in style[m].items()}} for m in sorted(style)}}
(OUT / 'summary.json').write_text(json.dumps(summary, indent=1))
print(json.dumps(summary, indent=1))
