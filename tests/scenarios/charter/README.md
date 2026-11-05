# Charter-offering acceptance promises: pointers

This folder held nine promise-first acceptance scenarios, copied from core's charter-offering suite
(`spec-kitty/spec-kitty`, `tests/scenarios/charter/`). Their Acts drove core's Python CLI, which is no
longer the oracle (ADR-0055 D3), and no runner in this repository ever executed them. #374 gave each
promise a verdict (re-scope of #31: "reduce to a pointer once every promise is mapped"), so the
`.feature` files are gone and only the pointers are left.

[`PROMISES.md`](PROMISES.md) names, for each promise, what accounts for it now:

- the contract scenario in `contracts/charter/scenarios/` that covers it, run by `CharterContractSuite`;
- or the `@WIP` scenario or reserved pin that holds its place, with the decision record that moved it;
- or the follow-up issue that tracks it.

New behavior scenarios are contract scenarios: write them in `contracts/charter/scenarios/`, under the
rules of its `README.md`.
