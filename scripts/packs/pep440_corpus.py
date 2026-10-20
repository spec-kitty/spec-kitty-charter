#!/usr/bin/env python
"""Generate the PEP 440 specifier-set corpus that Pep440Test checks the Java validator against.

Run with the venv core pins, isolated:

    ~/.venv-sk/bin/python -I scripts/packs/pep440_corpus.py \
        service/charter-domain/src/test/resources/pep440/specifiers.tsv

The oracle is packaging.specifiers.SpecifierSet(input): the input is valid when the constructor does not raise.
The file has '#' header lines (the packaging version and this command), then `input<TAB>valid` rows. The input
is written with every character outside printable ASCII, and the backslash, as a \\uXXXX escape of one UTF-16
unit, so tabs, newlines and non-ASCII digits survive a text file. `valid` is `true` or `false`.
"""

import itertools
import sys

import packaging
from packaging.specifiers import InvalidSpecifier, SpecifierSet

ADVERSARIAL = [
    "",
    ">=1.0",
    ">=1.0,<2",
    "~=1.4.5",
    "~=1",
    "==1.*",
    ">=1.*",
    "!=1.0.*",
    "===foobar",
    "==1.0+local",
    ">=1.0+local",
    "1!2.0",
    ">=1!2.0",
    ">=1.0a1",
    ">=1.0.post1",
    ">=1.0.dev1",
    ">=1.0rc1.post2.dev3",
    ">=1.0RC1",
    " >= 1.0 , < 2 ",
    ">=1.0,",
    ",>=1.0",
    ",,",
    ">=\u0661.0",
    "banana",
    ">=4.0.0rc7,<5",
    # more shapes the Specifier regex decides
    "  ",
    ",",
    "1.0",
    "==",
    ">=",
    ">= 1.0",
    ">=\t1.0",
    ">=\n1.0",
    ">=\u00a01.0",
    "\u00a0>=1.0\u00a0",
    ">=1.0\u2003,\u2003<2",
    ">=\x1c1.0",
    "=>1.0",
    "=1.0",
    "<>1.0",
    "!1.0",
    "~=1.0.*",
    "~=1.0+local",
    "~=1!1.0",
    "~=1.0a1",
    "~=1.0.post1.dev1",
    "==1.0.*.*",
    "==1.*.0",
    "==1.0.*+local",
    "==1.0a1.*",
    "==1.0.post1.*",
    "==v1.0",
    ">=V1.0",
    ">v1.0",
    "==1.0-1",
    "==1.0-r1",
    "==1.0_post_1",
    "==1.0-dev",
    "==1.0.dev",
    "==1.0a",
    "==1.0-alpha-1",
    "==1.0preview2",
    "==1.0PRE2",
    "==1.0c1",
    "==1.0.rev3",
    "==1.0.r",
    "==1.0+Local.Part-1_2",
    "==1.0+",
    "==1.0+.a",
    "==1.0+a..b",
    "==1.0+a.b.",
    "==1.0+a_b-c.d",
    "!=1.0+abc",
    "===1.0+abc",
    "===",
    "=== foo",
    "===foo bar",
    "===foo;bar",
    "===foo)",
    "===1.0.*",
    "===\u00e9",
    "===\u00a0foo",
    "===foo,>=1.0",
    ">=1.0,===x",
    "==1.0.po\u017ft1",
    ">=1.0.po\u017ft1",
    "~=1.0.po\u017ft1",
    ">=1.0+\u212a",
    "==1.0+\u212a",
    "==1.\u0663",
    ">=1!",
    ">=!1",
    ">=1!2!3",
    ">=01.02",
    ">=1.0.0.0.0.0.0.0.0.0.0",
    ">=99999999999999999999999.1",
    ">=1..0",
    ">=1.",
    ">=.1",
    ">=1.0 ,<2",
    ">=1.0, <2",
    ">=1.0 <2",
    ">=1.0;python_version>'3'",
    ">=1.0 ; python_version>'3'",
    "(>=1.0)",
    ">=1.0)",
    ">=1.0,<2,!=1.5,!=1.6.*",
    ">=1.0,>=1.0",
    ">=2,<1",
    "<=1.0.post1.dev2",
    "<1.0.dev0",
    ">1.0.post0",
    ">=1.0-1.dev1",
    ">=1.0-1.post1",
    ">=1.0a1.dev1.post1",
    ">=1.0.dev1a1",
    ">=1.0.dev1.dev2",
    ">=1.0a1a1",
    ">=1.0rc",
    ">=1.0.rc.1",
    ">=1.0_rc_1",
    ">=1.0-rc-1",
    ">=1.0\x00",
    ">=1.0\u200b",
    "\ufeff>=1.0",
]

OPS = ["==", "!=", "~=", ">=", "<=", ">", "<", "==="]
VERSIONS = [
    "1",
    "1.0",
    "1.2.3",
    "2024.1.2.3",
    "1!1.0",
    "1.0a1",
    "1.0b2",
    "1.0rc3",
    "1.0.post1",
    "1.0.dev4",
    "1.0a1.post2.dev3",
    "1.0.*",
    "1.*",
    "1.0+abc.1",
    "1.0.0.0.0",
    "v1.0",
    "1.0-1",
    "1.0.POST1",
    "x",
]
SPACINGS = ["", " ", "\t"]
# Long but well-formed (or one character off) values: a regex that recurses once per repeated group overflows the
# stack on these, and packaging accepts the valid ones whatever their length.
LONG = [
    ">=1" + ".1" * 10000,
    ">=1" + ".1" * 10000 + ".",
    "==1.0+a" + ".a" * 10000,
    "==1.0+a" + ".a" * 10000 + ".",
]


def valid(text: str) -> bool:
    try:
        SpecifierSet(text)
    except InvalidSpecifier:
        return False
    return True


def generated():
    for op, version, spacing in itertools.product(OPS, VERSIONS, SPACINGS):
        yield f"{op}{spacing}{version}"
    clauses = [">=1.0", "<2", "!=1.5", "~=1.4", "==1.*", ">=1.*", "bad", ""]
    for first, second in itertools.product(clauses, repeat=2):
        yield f"{first},{second}"
        yield f"{first} , {second}"
    for first, second, third in itertools.product(clauses[:4], repeat=3):
        yield f"{first},{second},{third}"


def encode(text: str) -> str:
    out = []
    for char in text:
        if char == "\\" or not (0x20 <= ord(char) < 0x7F):
            units = char.encode("utf-16-be")
            for i in range(0, len(units), 2):
                out.append("\\u%02x%02x" % (units[i], units[i + 1]))
        else:
            out.append(char)
    return "".join(out)


def main(argv):
    seen = set()
    rows = []
    for text in [*ADVERSARIAL, *generated(), *LONG]:
        if text not in seen:
            seen.add(text)
            rows.append((text, valid(text)))
    lines = [
        f"# PEP 440 specifier-set corpus, decided by packaging {packaging.__version__}"
        " (packaging.specifiers.SpecifierSet).",
        "# Generated by: ~/.venv-sk/bin/python -I scripts/packs/pep440_corpus.py <this file>",
        "# Columns: input<TAB>valid. The input is written with \\uXXXX (UTF-16 unit) for every character"
        " outside printable ASCII and for the backslash.",
    ]
    lines += [f"{encode(text)}\t{str(ok).lower()}" for text, ok in rows]
    text = "\n".join(lines) + "\n"
    if len(argv) > 1:
        with open(argv[1], "w", encoding="ascii", newline="\n") as handle:
            handle.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main(sys.argv)
