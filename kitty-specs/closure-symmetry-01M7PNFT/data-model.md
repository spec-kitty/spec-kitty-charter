# Data model: Closure symmetry

## `ConfigSchemas` (domain port, `packs.domain`)

| Operation | Returns |
|-----------|---------|
| `schemaProblems(ConfigValue schema)` | the problems that stop the schema compiling offline; empty when it compiles |
| `valueProblems(ConfigValue schema, Optional<ConfigValue> value)` | the problems of the value against the schema; empty when it fits |

## `ReferenceClosure` (domain rule, `packs.domain.rules`)

Built from a subject:

| Field | Meaning |
|-------|---------|
| assets | the candidate assets that built, in read order |
| payloads | the pack-relative paths of the asset payloads under `assets/` |
| guards | the candidate guards that built |
| namable templates | the ids of the mission types' own templates (`<type>/<path>`) |
| schemas | the `ConfigSchemas` port |

Invariants (the cascade, as the load applies it today):

1. An asset closes when it has no `executable`, or its `entry` is a payload path.
2. A guard closes when its `config_schema` (if any) compiles; and, when asset-backed, its asset is local, held among the
   assets that close (first by id), and declares an `executable`.
3. A lifecycle closes when every guard entry is local, names a guard that closes (first by id), and its `config` fits
   that guard's `config_schema` (a `config` for a guard with none is refused).
4. A mission type closes when every output template it names is one of the namable templates.

Operations: `firstUnresolved(artifact) -> Optional<String>` (the load's reason); `findings(artifacts) -> List<Finding>`,
`Finding(Urn artifact, String reason)`.

## `ReferenceFacts` (on `PackContents.Assembly.Assembled`, built from the shaped read)

| Field | Meaning |
|-------|---------|
| candidates | `Placed(file, artifact)` for each asset, guard, lifecycle and mission type the read built |
| payloads, templates | as above |
| schemas | the port |

`ReferenceFacts.NONE` for facts a test writes by hand. `ReferenceRules.RULES` maps each finding to a `Reason`
(`artifact_schema_error`, the candidate's file, the reason).

## `Pack.without`

`Pack.without(Collection<Urn>)`: the aggregate a removal leaves. The commit store reads the pack and takes the removed
artifacts out itself (plan review finding 5), so `PackRemovalStore.Removal` is unchanged.

## Views of the missions slice

`ReferenceFacts.LifecycleView` and `ReferenceFacts.MissionTypeView`: the packs domain does not name the missions slice
(`SLICES_DO_NOT_CROSS`), so the storage edge (`ReferenceSubjects`) gives the rule a lifecycle's guard entries and a
mission type's output templates.
