### Changed

- The scenario step `Given the registration jobs are held before they run` is `Given the registration workers are busy`
  (#659). It never held the scenario's own job: both workers run fillers that wait in the storage room, so the next job
  waits `pending`, as the jobs' queue holds a job while both workers are busy. Scenarios:
  `registration/whole-pack-registration.feature` (three scenarios) and the step vocabulary of `scenarios/README.md`. A
  consumer that binds the contract's scenarios binds the new wording and drops the old one; no scenario outcome changes.
