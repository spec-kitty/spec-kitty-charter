---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T12:28:33Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP13
---

# WP13 review, cycle 1 (reviewer-renata): changes requested

Gate: `verifyAll -PopenPacksDir=<22c14b2 checkout>` after `syncPacks`, with `:packs:test --rerun` and
`:packs:openPacksRoundTrip --rerun`, is green. `:packs:test` ran 3,067 tests with 0 failures; the archive suites ran
118 (28 + 6 + 84). The path rules, link, device and type refusals, the byte bounds, the write modes and the cleanup all
held under attack (see "What held" below). The rejection is for two resource bounds that the library breaks before the
extractor's own checks run. Both were reproduced against commons-compress 1.28.0 with the exact builder options
`ZipReader` uses.

## Issue 1 (blocking, NFR-004): the zip central directory is parsed in full before any bound applies

`ZipEndRecord.entryCount` reads the count from the EOCD record, but `ZipFile` ignores that count. It walks central
directory headers for as long as it finds their signature, and builds every `ZipArchiveEntry` and its name map
before `inspect` counts anything. Two reproductions, each a body under 32 MiB:

1. **EOCD that lies about the count.** The EOCD says 1 entry; the central directory holds 600,000 minimal headers
   (a 29.9 MB file). `ZipFile.builder()...get()` parses all 600,000 and keeps about 428 MB of heap. Only then does
   `inspect` refuse at entry 10,001. The corpus row "zip with 10,001 central entries" (`declaredCount(1)`) passes, but
   only because the count catches it after the parse. NFR-004 says the zip count is taken "from the
   end-of-central-directory record before any entry is read". As written, every central entry is read first.
2. **A flood of extra fields (honest count).** Each central header carries 64 KiB of 4-byte empty extra-field
   records (16,000 distinct unknown ids). Opening `ZipFile` takes about 1 s of CPU per such entry: 20 entries
   (1.28 MB) took 20.2 s. A 32 MiB body holds about 500 of them, which is several minutes of CPU per upload, all
   inside the builder and before the extractor sees an entry. The merging of extra fields in `ZipArchiveEntry` costs
   O(n²).

**Fix:** before building `ZipFile`, walk the central directory yourself from the EOCD (or ZIP64) offset and size.
That code already exists in `ZipEndRecord`:
- refuse `TOO_MANY_ENTRIES` as soon as the walk passes `maxEntries` headers, and refuse when the headers found differ
  from the declared count, or overrun the declared central-directory size or the file;
- cap the extra-field length (and/or record count) per central header and refuse beyond it, for example with
  `ZIP_EXTRA_FIELD` or a new code. The local headers you already read in `ZipLocalHeader` need the same cap;
- add corpus rows for both reproductions. Each should assert the refusal code and that the walk refuses without
  opening `ZipFile`, for example through a package-private seam or a counting channel.

## Issue 2 (blocking unless the operator defers it): PAX header flood, heap amplification in tar

A tar.gz holding one PAX `x` header of 28 MiB, made of 3,000,000 distinct `k=v` records, decompresses within the
32 MiB + 64 KiB bound. `TarArchiveInputStream.getNextEntry()` then keeps all 3,000,000 records in the entry's
extra-PAX map, about 384 MB of heap. The records gzip well, so the upload is a fraction of 32 MiB. Global `g` headers
build up the same way, and a GNU `L`/`K` long name can be 32 MiB too. The decompressed-byte bound holds; the memory
does not.

**Fix:** bound the meta headers before the library parses them. For example, put a small record-level guard between
`plain` and `TarArchiveInputStream`: track 512-byte header records, read the size and type flag, and refuse a
`x`/`g`/`L`/`K` record whose size is over a cap such as 64 KiB (`PATH_TOO_LONG` or `ARCHIVE_UNREADABLE`). Add a corpus
row. If you and the operator rule this a follow-up instead, record the ruling and the issue number in the hand-off.

## Issue 3 (required, small): stale PIT comments after the runner's revert of `infra.api`

- `service/packs/build.gradle.kts`, the floor comment: "infra.api is mutated (above) but has no floor yet". It is not
  mutated: `mutatedPackages` leaves it out after `3e1cf95b`.
- `service/packs/pit-targets.init.gradle`, the header: it lists `infra.api` in the packs scope and drops it from the
  exclusions. Both are false now.

The code is right; make the comments match it.

## Nits (not blocking; fix them if you are in the file anyway)

- `ZipReader.readSpooled` keeps writing the remaining planned entries after a `ZIP_SIZE_MISMATCH`. The writes stay
  bounded and are discarded, but they are wasted work. Stop when `!tree.clean()`.
- `ZipReader.read`: when a file named `upload.zip.spool` already exists in the staging root, `CREATE_NEW` fails and the
  `finally` deletes that file, which belongs to the caller. Delete the spool only when this call created it.
- A `RuntimeException` from commons-compress on malformed input propagates as-is, so the caller would likely answer
  `500`, not `422`. Consider mapping library runtime exceptions during reading to `ARCHIVE_UNREADABLE`.
- A PAX or GNU long name that starts with `/` is made relative by the library and then extracted (the benign row
  "tar PAX absolute path..."). It cannot escape: `/../x` becomes `../x` and is refused. But IC-03 and NFR-005 say
  absolute paths are refused. Either refuse it, or record the deviation for the docs WP's divergence register.
- NFR-012 escaping covers ISO controls and U+2028/2029. Consider escaping bidi and format characters (Cf, such as
  U+202E) as well; non-ASCII names are refused, but their values are still echoed back.
- Ownership: the three `badfixture` classes under `service/deployable/src/test/java/.../packs/infra/{api,fetch,archive}/badfixture/`
  are outside `owned_files`. They are the `HexagonalArchitectureTest` fixtures, so this is fine, but list them in the
  hand-off.

## What held (no change needed)

- Traversal through `..`, `\`, NUL (zip raw name and PAX), absolute paths, drive letters, empty segments, `.`
  segments, over-4,096-byte paths, and GNU long names with `..`. Names are checked after PAX/GNU application.
- Links, hard links, devices, FIFOs, sockets, sparse files, and the `7` and `D` flags, through the link-flag allowlist.
  Zip Unix modes: S_IFMT must be 0, a regular file or a directory.
- Collisions after NFC and case folding, a file where a directory is, and the reverse. Stripping `./` cannot create a
  collision that the ledger misses (`./a` and `a` collide), and the shared leading directory is only resolved, never
  renamed.
- Bounds: tar is limited on the compressed body (probe) and on the decompressed stream (strict, 32 MiB + 64 KiB).
  Declared sizes are reserved before reading. A zip that lies about its size is cut at the remaining budget, plus 1
  byte. Overlap detection on spans sorted by start is complete (comparing adjacent spans finds any overlap).
- Writing: `CREATE_NEW`, `NOFOLLOW_LINKS`, `0600`/`0700`, and a fresh `pack/` directory. The spool is owner-only and
  deleted. On refusal `discard()` leaves the staging root empty, and the corpus asserts that the parent's listing is
  unchanged.
- Violations are escaped and truncated to 256 bytes on a character boundary, with at most 100 listed and the rest
  counted.
- Supply chain: the `verification-metadata.xml` diff only adds entries: commons-compress 1.28.0 jar and pom (sha256
  `e1522945…42ca` matches the cached jar), commons-codec 1.21.0 (the BOM version; the POM asks 1.19.0), the
  commons-io 2.20.0 jar, and commons-parent 96 (the parent of codec 1.21.0). No optional codecs. `verify-metadata`
  stays true. The version differences from the plan's guesses are BOM resolution and acceptable.
- PIT: `infra.archive` is mutated with floor 80. `infra.api` stays out. `git-host` is out of `test`, and `stress` is
  out of PIT only.
- Hexagonal: the port types are in `application`, the adapter ring is added, and the bite tests are present.
- Red-first: `611a6e36` uses a stub extractor (90 of 94 tests failing on "must be refused"). No suppressions, no
  `@Disabled`.
