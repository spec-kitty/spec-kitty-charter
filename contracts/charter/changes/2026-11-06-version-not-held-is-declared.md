### Changed

- `ActivationInvalid` names the pack pinned to a version the service does not hold: a `422 activation_invalid` with the
  violation `version_not_held` at `/packs/{i}/version`, whose `detail` names the held and the pinned version
  ("The pack 'alpha' is held at 1.0.0, not 2.0.0."). A new example, `activation-version-not-held`, shows it, and the
  scenario that pins the refusal pins the detail too. The behavior is unchanged (operator ruling of 2026-11-06).
