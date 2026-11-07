# Decision Moment `01M6V4WMF8CD8NBN7A065E9ZQ5`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp14-unreadable-rename`
- **Input key:** `wp14-review-cycle-1`
- **Status:** `resolved`
- **Created:** `2026-11-07T13:59:08.520907+00:00`
- **Resolved:** `2026-11-07T14:00:09.385757+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude:opus:runner`
- **Other answer:** `true`

## Question

WP14 review cycle 1: how does registration treat an unreadable stored pack, a rename, and a replace that takes another pack's name?

## Options

_(none)_

## Final answer

(1) An unreadable stored pack is diffed against what can still be read: the artifact ids from its file listing (each file read on its own; a file that cannot be parsed still counts by the id its path names) and the edges of any graph document that parses. Removed artifacts and dropped edges found that way go through the gate and the inbound scan as usual. If the stored pack's artifact set cannot be determined at all, the registration is refused 500 pack_unreadable (the operator repairs it), never treated as empty. (2) The inbound scan matches qualified references against both the stored pack's name and the uploaded name. (3) A replace that changes the pack's name to a name another stored pack holds is refused with the refusal create uses for a duplicate name.

## Rationale

Review cycle 1 probes p1a-p1c and p4 showed the gate and the FR-036 scan bypassed through a legacy file or a rename; p3 showed names stop being unique. The repair path stays but can no longer skip locks or referrers. No contract shape changes.

## Change log

- `2026-11-07T13:59:08.520907+00:00` - opened
- `2026-11-07T14:00:09.385757+00:00` - resolved (final_answer="(1) An unreadable stored pack is diffed against what can still be read: the artifact ids from its file listing (each file read on its own; a file that cannot be parsed still counts by the id its path names) and the edges of any graph document that parses. Removed artifacts and dropped edges found that way go through the gate and the inbound scan as usual. If the stored pack's artifact set cannot be determined at all, the registration is refused 500 pack_unreadable (the operator repairs it), never treated as empty. (2) The inbound scan matches qualified references against both the stored pack's name and the uploaded name. (3) A replace that changes the pack's name to a name another stored pack holds is refused with the refusal create uses for a duplicate name.")
