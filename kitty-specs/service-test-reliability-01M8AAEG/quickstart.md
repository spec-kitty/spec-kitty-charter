# Quickstart: Service tests wait for states; test hooks out of main

## Run the changed suites

```bash
git fetch --unshallow --tags origin   # once, in a shallow checkout
env -u _JAVA_OPTIONS ./gradlew :kernel:test :packs:test :activations:test :deployable:test
env -u _JAVA_OPTIONS ./gradlew verifyAll
```

Clear only `_JAVA_OPTIONS`; keep `JAVA_TOOL_OPTIONS` (the proxy and trust store).

## Reproduce one mutant

1. Copy `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java` aside.
2. Delete the line `WorkMeter.count(StoreWork.LOOKUP_PARSE);` in `locate`.
3. Run `./gradlew :packs:test --tests '*WholePackStoreTest'`: `aLookupByIdIsCounted` fails.
4. Copy the file back and check `git status` is clean.

Every mutant of the Mission, with its result, is listed in `research.md` under "Mutation notes".

## Hold a registration job in a test

- Running: wrap the jobs' storage room in a `HeldRoom` (packs test fixtures), call `hold()`, start the job, and
  `awaitHeld(1)`.
- Pending: hold the room and start `RegistrationJobs.RUNNING_LIMIT` fillers first; the next job waits in the queue.

## Wait for a state

`Await.until("what you wait for", () -> condition)` fails after 30 s naming the state. Never `Thread.sleep` to wait.
