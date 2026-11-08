---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T12:48:35Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP13
---

# WP13 review, cycle 2 (reviewer-renata): changes requested (one item)

Gate: I ran `syncPacks`, then `verifyAll -PopenPacksDir=<22c14b2 checkout>` with `:packs:test --rerun` and
`:packs:openPacksRoundTrip --rerun`, on lane head 50eb2cfc. It is green: `:packs:test` ran 3,093 tests with 0
failures, 96 of them in the corpus, 29 in the extractor tests, 13 in ZipBoundsTest and 6 in LimitedInputStreamTest.
Cycle 1's issues 2 and 3 and every nit are resolved. Issue 1 is resolved except for the bypass below. I reproduced it
against the lane's compiled classes, through the package-private `ZipOpener` seam.

## Issue 1 (blocking, NFR-004): a ZIP64 locator sends the library to a central directory the walk never saw

`ZipEndRecord.locate` follows the ZIP64 locator only when an EOCD field holds its overflow value (`0xFFFF` /
`0xFFFFFFFF`). Commons Compress 1.28.0 (`ZipFile.positionAtCentralDirectory`) checks the 20 bytes before the EOCD for
the ZIP64 locator signature regardless of the field values. When it finds one, it takes the central directory offset
from the ZIP64 record. So the walk and the library can look at two different directories.

Reproduction (30.5 MB body):
- The EOCD is not in overflow. It declares count 1, plus the offset and size of a small central directory with one
  header.
- A ZIP64 locator sits just before the EOCD. It points to a ZIP64 record whose offset and size name a second central
  directory of 600,000 headers.

`ZipCentralDirectory.verify()` walks the small directory and passes (1 header, count 1). The library then opens and
parses 600,001 entries. That is the same heap amplification as in cycle 1, about 430 MB, and the walk never stopped
it. The extractor does refuse in the end, but only after the library has built every entry.

**Fix:** decide where the directory is the way the library does. When the locator signature is at `endAt - 20` (and
`endAt > 20`), always use the ZIP64 record's count, offset and size. Alternatively, refuse with `ZIP_COUNT_MISMATCH`
(or `ARCHIVE_UNREADABLE`) when a locator is present and the EOCD's non-overflow fields disagree with the ZIP64
record. Both ways remove the divergence. Add a `ZipBoundsTest` row for this shape that asserts the refusal and that
the opener is never called.

## Checked and holding (your specific questions)

- **The `readRecord()` hook.** Every header record reaches the guard: the four `header_too_large` rows and the three
  `/`-path rows pass.
- **Multi-record meta bodies.** The body is captured through the filter stream up to the declared size, at most
  64 KiB.
- **EOF.** A null record and the zero end-of-archive records are not meta records.
- **Old GNU sparse extension records.** These also pass through `readRecord`, so a byte at offset 156 can look like
  a type flag. At worst that gives a false refusal, and sparse entries are refused anyway.
- **A global PAX `size`.** I tried a global `g` record with `size=<28 KB>` ahead of an `x` header whose raw size is
  0, and the same against an `L` header. The library does not take the global size for the meta header (it fails with
  "Corrupted TAR archive", which becomes `archive_unreadable`), so this does not get around the raw-size cap.
- **ZIP64 field offsets in the walk.** The offsets of total, size and offset (32, 40 and 48) are correct. A negative
  offset or one past the file is refused.
- **Name and comment lengths.** Leaving these uncapped is acceptable. The walk caps the headers at 10,000 and the
  spool at 32 MiB, so names and comments stay within about 2x of the body in memory, at linear cost.
- **The 1,024-byte extra cap.** The worst case left is 10,000 headers, each with 256 empty extra fields. That opens in
  about 2.7 s, because merging extra fields is still quadratic. This is acceptable; a cap on the record count (for
  example 32 per header) would make it negligible, but it is optional.
- **`RuntimeException` to `archive_unreadable`, with `UncheckedIOException` rethrown.** This is correct.
- **The other changes.** The spool is created before the `try`. Writing stops after a size mismatch. Cf characters
  are escaped. The PIT comments are fixed. The changed test (`theExpandedBudgetShrinksWithEveryFileAZipWrites`) is a
  legitimate consequence of the stop-on-mismatch change.
- **Red-first.** `7f7d2910` came before `3c658094`.
