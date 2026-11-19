# Data model: Pack id split

| Type | Module / package | Value | Invariant | Holds |
|------|------------------|-------|-----------|-------|
| `PackIdentifier` | kernel `kernel.domain` | `String value` | `IdentifierGrammar.slug(63)`; refused at construction with `GRAMMAR_DETAIL` | the registered pack id: the registry name, the directory name of a live pack, the URL path segment, `CharterArtifact.packId()` |
| `PackUlid` (new) | kernel `kernel.domain` | `String value` | ULID: 26 characters `[0-9A-HJKMNP-TV-Z]`; refused at construction | the descriptor's `pack_id`; the key of `EdgeIndex`, `ClosureView` ordering, `MergedClosure.Winner.descriptorPackId`, `ClosureSnapshot` |
| `PackDirectory` (new) | packs `packs.infra.storage`, package-private | `String name` | one path segment: not blank, no `/` or `\`, not `.` or `..` | a directory directly under the pack domain: a live pack, `.pack-commit-*` staging, `<id>.pack-old-*` aside, a link's real target |
| `RefusalPack` | kernel | `(PackUlid descriptorPackId, PackIdentifier packId, String name)` | non-null | a pack named by a refusal; wire unchanged (strings) |
| `ProfileEnhancementGateway.Dropped` / `Enhancing` | packs | `pack: PackIdentifier` | — | the enhancing pack, by registered id |
| `PackRef` | activations domain | `(PackIdentifier packId, Optional<String> version)` | the pack id grammar | an activation pin |

**Conversions** (the only ones allowed): `PackDirectory.of(PackIdentifier)`; `PackUlid.of(descriptor.packId())` at the
read that assembles the descriptor's facts. There is no conversion between `PackIdentifier` and `PackUlid`: the registry
maps one to the other through the configured packs.
