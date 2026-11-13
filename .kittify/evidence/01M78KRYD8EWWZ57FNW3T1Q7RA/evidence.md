#110 delivered in PR #542 (head 62dded1): manifest membership derived from the held pack and restamped on every write
(registration, PUT, DELETE, relationship write, WholePackStore.write, gate); generated where absent; a registration
refuses a manifest whose lists differ (422 manifest_membership). ADR-0042 amendment 2026-11-12; ADR-0036 manifest row
amended. Tests first (ManifestMembershipTest, 15 cases). Adversarial squad (renata, alphonso, ivan) folded in 62dded1.
CI green on 62dded1: gate, Service/verify, whole-pack, docs cleanliness. Local verifyAll green (packs 4869, deployable 1705).
