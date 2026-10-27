The configuration root the deployable's `ConfigRootTestConfiguration` copies into a temporary directory for a test:
`packs/<pack>/` holds the packs (each at the level its `pack.yaml` declares), `activations/` is reserved for the
activation documents. Tests never write here; they write to the copy.
