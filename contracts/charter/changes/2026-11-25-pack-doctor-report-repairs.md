### Added

- `PackDoctorReport` gains `repairs` (operator ruling Q14 of 2026-11-25, #651): the report `pack-doctor remediate`
  writes after its outcome lines always carries it, and `pack-doctor validate`'s never does. It lists each quoted number
  remediate wrote plain in a pack it rewrote, as `pack`, `file`, `pointer`, `value` (the scalar's value as the file held
  it, now written as a number) and `type` (`number` or `integer`), per pack in the order given and within a pack as its
  outcome lines name them; it is empty when remediate wrote none, and a refused or unchanged pack has none. The XML
  mapping names its items `repair`. No released candidate has a remediate report, so no reader breaks. Core's seam:
  none; the CLI and its report are not part of the API core calls, and core regenerates nothing.
