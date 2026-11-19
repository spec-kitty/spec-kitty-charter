### Breaking

- A pack holds no symbolic link below its root (#597, rulings D1 and D6 of #478). A link inside a pack, to a file, a
  directory or nothing, is never read through, and is refused with the new violation code `pack_entry_linked`
  (`validation_failed`, `422`), one violation per link at the link's pack-relative path, in the words
  `<path>: is a symbolic link; a pack holds no links`:
  - a registration of a staged tree holding a link is refused, and nothing is stored (#597's probe registered such a
    tree `CREATED`, with no diagnostic);
  - validate mode reports the same violation;
  - the service's load of a pack in a configured directory refuses a pack holding a link: the composing read answers
    `pack_unreadable` naming the link, as for another fault of the pack's content.

  The pack root itself may still be a link. Hard links and Windows junctions are not symbolic links and are out of
  this rule.
- Core's seam: a client that registered, validated or configured a pack holding a link now reads the refusal above;
  remove the link from the pack and put the file it led to in its place.
