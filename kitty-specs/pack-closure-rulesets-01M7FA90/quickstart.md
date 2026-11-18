# Quickstart: check slice 5 by hand

```bash
# Unit tests of the rules and mappers.
./gradlew :pack-validation:test :packs:test --max-workers=1

# The contract scenarios: the wire codes and their order.
./gradlew :deployable:test --max-workers=1

# The whole gate.
./gradlew verifyAll --max-workers=1
```

To see Q1 at work, register `examples/v1` plus `notes.txt`. The registration ends `succeeded`, with one
`file_not_held` diagnostic at `notes.txt`, and `GET` of the pack does not list the file.
