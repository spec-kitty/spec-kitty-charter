### Breaking

- Two packs at one level that define the same step enhancement id are now refused `500 pack_sibling_conflict` on every composing read (e.g. `getOffering`). Before, the service did not hold step enhancements and composed such packs, ignoring the files (#428). A descriptor `overrides` entry settles the contest as for any other kind, and a same-id enhancement at a higher level replaces the lower one by layer override (ADR-0048 d10).
