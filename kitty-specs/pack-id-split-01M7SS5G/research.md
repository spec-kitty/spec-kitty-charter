# Research: Pack id split

The census is in `research/census.md` (67 production sites, file:line). The rulings are in `research/decisions.md`.

## D1 — Descriptor identity type

- **Decision**: a kernel `PackUlid` guarding the ULID grammar (26 characters, Crockford base32 without I, L, O, U).
- **Rationale**: the descriptor schema's own `$defs/Ulid`, so a valid pack never trips the guard. The canonical-fixtures rule moves the
  fake ULIDs (`01PACK` ×14, `02OTHER` ×4, `01TOOLPACK`) to real ones.
- **Alternatives**: a not-blank-only `DescriptorPackId` (rejected: it keeps tests on a non-canonical shape).

## D2 — The identity artifacts carry

- **Decision**: the registered `PackIdentifier`, everywhere. `Artifact.packId` is "as the request named it". Read paths
  that assemble under the ULID (`StoreRead.of`, `ArtifactFiles`, `EdgeIndex.EdgeNode`) take the registered id from the
  caller. Validate mode is caller-passed and defaults to the directory name (D2b), and #478's CLI keeps that default.
- **Alternatives**: both ids on each artifact; the ULID (rejected: the wire states the registered id).

## D3 — Locator

- **Decision**: `PackLocator.locate` matches the configured name only. The edge index resolves a holder through the
  closure by `PackUlid`, and `holdingPack`'s `orElse(referringPack)` tolerance goes.
- **Rationale**: no second reader (charter). The HTTP `PackId` pattern already returns 400 for a ULID, so no client loses an answer.

## D4 — Directory keys

- **Decision**: a package-private `PackDirectory(String name)` in `packs.infra.storage`. It is one path segment, not
  `.` or `..`, with no separator, and `PackDirectory.of(PackIdentifier)` converts. `FilesystemPackStore.directoryOf`
  keeps its under-the-root check.
- **Alternatives**: a `Path` or String (rejected: it loses the check, and the type names nothing).

## D5 — Refuse on read

- **Decision**: `StorageCodes.PACK_ID_INVALID` (`pack_id_invalid`, a violation code). `StorageRootLocations` refuses the
  entry with it through its existing `refuse(entry, reason)` (WARN log; the entry is not served), before `location(...)`
  is built.
- **Alternatives**: a kernel `RefusalCode` on the wire (rejected: a non-pack-id is unaddressable over HTTP, so there is
  no request to answer).

## D6 — New staged pack

- **Decision**: `StagedTree` carries the registered id the upload targets. `InboundReferences.membersOf` uses it in place
  of the descriptor `name`.

## D7–D9 — Folded

- PackPin.packId moves to the pack id grammar (`^[a-z0-9]+(-[a-z0-9]+)*$`, ≤ 63) in both the offering JSON schema and
  the charter YAML, with a `### Breaking` fragment. `PackRef` wraps `PackIdentifier`.
- The `PackId.yaml` description reads "The id the Charter Pack is registered under in the service's pack registry."
- `ProfileEnhancementGateway.Dropped.pack` and `Enhancing.pack` become `PackIdentifier`.

## Supply chain

No dependency changes. DIRECTIVE_051 is not engaged.
