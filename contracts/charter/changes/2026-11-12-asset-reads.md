### Added

- `listPackAssets` (`GET /packs/{packId}/artifacts/asset`), `getPackAsset` (`GET …/asset/{artifactId}`) and
  `getPackAssetPayload` (`GET …/asset/{artifactId}/payload`), tag `Assets` (#105; ADR-0074 decisions 2, 9 to 11 and
  13). The list answers `AssetList {items: [AssetSummary {id, mime, executable, _links}], _links}` in id order,
  unpaged. The item answers the `Artifact` envelope with member `asset` and links `self`, `payload` (the bytes) and
  `collection` (the asset list). The payload answers the bytes the pack holds, unchanged, with the asset's `mime` as
  `Content-Type` when it is a well-formed `type/subtype` (validate's one rule) and `application/octet-stream`
  otherwise; it ignores `Accept`, and every `200` carries `X-Content-Type-Options: nosniff` and
  `Content-Security-Policy: sandbox; default-src 'none'`, and no `Content-Disposition`, on purpose (headers `XContentTypeOptions`, `ContentSecurityPolicy`). All three answer the
  pack revision as `ETag` and the source fingerprint; the payload answers `304` to a matching `If-None-Match`,
  `*` included. An unknown pack is `404 pack_not_found`, an asset the pack does not hold `404 artifact_not_found`
  (response `AssetNotFound`). The relation `payload` joins the relation table and the lint ruleset's relation
  enum; the proposed relation `assets` (ADR-0019's `/packs/{packId}/assets`) is dropped (ADR-0074 decision 13). The
  three operations are `ready` in the switch-gate register.
