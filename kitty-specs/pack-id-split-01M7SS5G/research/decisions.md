# Open decisions (sent to the orchestrator, 2026-11-19)

Status: **pending ruling**. Recommendation in bold.

| # | Question | Options | Recommendation |
|---|----------|---------|----------------|
| D1 | The descriptor identity type | (a) kernel `PackUlid`, guards the ULID grammar; fake fixtures (`01PACK`) become real ULIDs · (b) `DescriptorPackId`, not-blank only | **(a)** |
| D2 | Which identity `CharterArtifact.packId()` carries | (a) always the registered id; the indexes key by `PackUlid` beside it · (b) both · (c) the ULID | **(a)**: `Artifact.packId` is "as the request named it" |
| D2b | Validate mode's id for an unregistered directory | (i) the directory name, refused by `pack_id_invalid` · (ii) a caller-passed id, falling back to (i) | **(ii)** |
| D3 | `PackLocator`'s fallback to the declared `pack_id` | (a) drop it; a separate lookup by `PackUlid` · (b) keep the dual lookup | **(a)** |
| D4 | Storage directory keys (`.pack-commit-*`, `.pack-old-*`) | (a) a storage-local directory record · (b) a plain `Path` or String | **(a)** |
| D5 | Refusal code on read | (a) `StorageCodes` `pack_id_invalid`: logged, not listed, configured or served · (b) a kernel `RefusalCode` on the wire | **(a)** |
| D6 | A new staged pack's member id in InboundReferences | (a) the upload's registered id · (b) a separate member-key type | **(a)** |
| D7 | Activation `PackPin.packId` grammar (`Slug`: `_`, 64) | (a) fold it in: the PackId grammar, a `### Breaking` fragment · (b) defer | **(a)** |
| D8 | `PackId.yaml` stale wording | fold the fix | **fold** |
| D9 | `Dropped.pack` / `Enhancing.pack` as String | type them as `PackIdentifier` | **fold** |
