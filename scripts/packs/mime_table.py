#!/usr/bin/env python
"""Print Python's built-in MIME tables as the body of AssetMimeTable.java.

Run isolated, from the repository root:

    ~/.venv-sk/bin/python -I scripts/packs/mime_table.py

Core's asset check calls mimetypes.guess_type, which also reads platform files such as /etc/mime.types.
This script reads only the tables compiled into Python: mimetypes.MimeTypes(filenames=()) with strict types
(plan call P8; the possible divergence from core is recorded in the DIV register). It prints the strict
extension -> type table, the suffix map (.tgz -> .tar.gz) and the encoding extensions (.gz, .Z, .bz2, .xz, .br),
which guess_type peels off before it looks at the type.
"""

import mimetypes
import platform


def entries(rows: list[str]) -> str:
    return ",\n".join(rows)


def main() -> None:
    table = mimetypes.MimeTypes(filenames=())
    print(f"// Python {platform.python_version()}: ~/.venv-sk/bin/python -I scripts/packs/mime_table.py")
    print("// TYPES: mimetypes.MimeTypes(filenames=()).types_map[True], sorted by extension")
    print(entries([f'            "{ext}", "{mime}"' for ext, mime in sorted(table.types_map[True].items())]))
    print("// SUFFIXES: mimetypes.MimeTypes(filenames=()).suffix_map, sorted by extension")
    print(entries([f'            "{ext}", "{new}"' for ext, new in sorted(table.suffix_map.items())]))
    print("// ENCODINGS: mimetypes.MimeTypes(filenames=()).encodings_map, sorted by extension")
    print(entries([f'            "{ext}"' for ext in sorted(table.encodings_map)]))


if __name__ == "__main__":
    main()
