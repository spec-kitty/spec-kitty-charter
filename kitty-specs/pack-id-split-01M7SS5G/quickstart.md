# Quickstart: verify the pack id split

```bash
./gradlew :kernel:test --tests '*PackIdentifierTest' --tests '*PackUlidTest'
./gradlew :packs:test --tests '*StorageRootLocations*' --tests '*PackLocator*'
# NFR-003: no production construction from a descriptor or staging value
grep -rn "new PackIdentifier(" --include=*.java service | grep /main/ | grep -E "descriptor\(\)|descriptorPackId|getFileName|\.name\(\)"   # expect 0 hits
./gradlew verifyAll
```

A manual check of US1: seed a domain with `a--b/pack.yaml` beside a valid pack and start the service. `GET /packs` lists
only the valid pack, and the log shows `pack_id_invalid` naming `a--b`.
