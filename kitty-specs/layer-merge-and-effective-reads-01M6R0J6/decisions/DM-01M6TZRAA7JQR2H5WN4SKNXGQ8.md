# Decision Moment `01M6TZRAA7JQR2H5WN4SKNXGQ8`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.archive-header-bounds`
- **Input key:** `archive_header_bounds`
- **Status:** `resolved`
- **Created:** `2026-11-07T12:29:24.167462+00:00`
- **Resolved:** `2026-11-07T12:29:27.005328+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP13 review: commons-compress parses the whole zip central directory and holds whole PAX headers in memory before our bounds apply. What bounds does the extractor add?

## Options

_(none)_

## Final answer

(1) Zip: the extractor walks the central directory itself from the EOCD (and ZIP64) record before opening ZipFile; it stops and refuses (too_many_entries) past 10,000 headers, refuses a count that does not match the headers found (zip_count_mismatch), and refuses a header whose extra-field length exceeds 1,024 bytes (zip_extra_field_too_large); ZipFile is opened only after the walk passes. (2) Tar: a PAX extended (x), PAX global (g) or GNU long-name/long-link (L/K) header larger than 64 KiB is refused (header_too_large) before the library parses it. (3) A PAX or GNU long name that begins with / is refused as path_absolute (the spec refuses absolute paths), not made relative. (4) A runtime exception from commons-compress becomes a 422 archive_unreadable refusal, never a 500.

## Rationale

WP13 review cycle 1 (reproduced against commons-compress 1.28.0); bounds the spec promises (NFR-004/005); fixed in WP13, not deferred.

## Change log

- `2026-11-07T12:29:24.167462+00:00` - opened
- `2026-11-07T12:29:27.005328+00:00` - resolved (final_answer="(1) Zip: the extractor walks the central directory itself from the EOCD (and ZIP64) record before opening ZipFile; it stops and refuses (too_many_entries) past 10,000 headers, refuses a count that does not match the headers found (zip_count_mismatch), and refuses a header whose extra-field length exceeds 1,024 bytes (zip_extra_field_too_large); ZipFile is opened only after the walk passes. (2) Tar: a PAX extended (x), PAX global (g) or GNU long-name/long-link (L/K) header larger than 64 KiB is refused (header_too_large) before the library parses it. (3) A PAX or GNU long name that begins with / is refused as path_absolute (the spec refuses absolute paths), not made relative. (4) A runtime exception from commons-compress becomes a 422 archive_unreadable refusal, never a 500.")
