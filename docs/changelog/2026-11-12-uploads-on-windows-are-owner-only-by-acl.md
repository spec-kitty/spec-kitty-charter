---
okf_version: "0.2"
title: Uploads register on Windows; staged files are owner-only by ACL there
description: The service makes what it stages owner-only with an ACL where the file system has no POSIX permissions, so a pack upload on the Windows native build no longer answers 500.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0046-service-release-line-and-native-packaging
  - adr/0065-pack-registration-replaces-the-whole-pack
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you run the service on Windows, or add code that writes to the staging area.
---

# 2026-11-12 — Uploads register on Windows

**A pack upload on the Windows native build no longer answers `500 internal_error`**
([#510](https://github.com/spec-kitty/spec-kitty-charter/issues/510)). The service created its staging and extraction
files with a POSIX permission attribute, which NTFS does not support, so every upload failed with
`'posix:permissions' not supported as initial attribute`.

- **What changed.** One helper, `OwnerOnly`, makes every path the service stages owner-only: the staging root and job
  directories, the uploaded or fetched archive, the extracted tree, a git work directory and the git version check's
  home. On a file system with POSIX permissions it sets mode `0700` or `0600` at creation, as before. On one with
  ACLs but no POSIX permissions (NTFS) it creates the path without the attribute and then replaces its ACL with one
  entry that allows the owner everything; a directory's entry is inherited by what is created in it.
- **A file system with neither is refused at start-up.** The service does not start when its staging directory is on
  such a file system, and names the directory. It never accepts an upload it cannot keep from other users.
- **For contributors.** No class of the packs module but `OwnerOnly` may build a POSIX mode attribute; an ArchUnit
  rule (`OwnerOnlyBoundaryTest`) holds it. The tests run the ACL path on Linux with Jimfs, configured like NTFS.
