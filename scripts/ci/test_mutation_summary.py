"""The mutation summary renders every module's floors.json, and says so when one is missing or red.

Standard library only (unittest), so it runs with either runner from the repository root::

    python3 -m unittest discover -s scripts/ci -p 'test_*.py'
    python3 -m pytest -p no:cacheprovider scripts/ci -q

The fixtures copy the shape ``checkMutationFloors`` (build-logic ``MutationFloors.toJson``) writes; the profiles one
is a real ``./gradlew :profiles:pitest`` summary. ``fixtures/floors.json`` is the key contract: build-logic's
``MutationFloorsTest`` holds it to exactly what ``MutationFloors.toJson`` writes, and the tests below hold this reader
to its keys.
"""

from __future__ import annotations

import io
import json
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mutation_summary as ms  # noqa: E402

# Written by MutationFloors.toJson, through build-logic's MutationFloorsTest (the key contract).
KOTLIN_FIXTURE = Path(__file__).resolve().parent / "fixtures" / "floors.json"
SUMMARY_KEYS = {"project", "mutants", "groups"}
GROUP_KEYS = {
    "group", "packages", "excludes", "mutants", "detected", "survived", "noCoverage", "timedOut", "score", "strength",
    "floor", "marginMutants", "marginPoints", "passed", "topClasses",
}

# The module list ``./gradlew mutationModules`` writes for these tests' modules (#388), sorted as the task sorts it.
MODULES = [
    "service/activations", "service/kernel", "service/missions", "service/missions-edge", "service/packs",
    "service/profiles", "service/profiles-edge",
]
TOP_CLASS_KEYS = {"class", "survived", "noCoverage"}

# A real summary, from `./gradlew :profiles:pitest` (MutationFloors.toJson's exact layout).
PROFILES = """{
  "project": ":profiles",
  "mutants": 66,
  "groups": [
    {
      "group": "profiles",
      "packages": ["ai.speckitty.charter.profiles"],
      "excludes": [],
      "mutants": 66,
      "detected": 35,
      "survived": 2,
      "noCoverage": 29,
      "timedOut": 0,
      "score": 53.0,
      "strength": 94.5,
      "floor": 50,
      "marginMutants": 2,
      "marginPoints": 3.0,
      "passed": true,
      "topClasses": [
        {"class": "ai.speckitty.charter.profiles.domain.AgentProfile", "survived": 2, "noCoverage": 29}
      ]
    }
  ]
}
"""


def group(name: str, *, score: str, floor: int, passed: bool, top: list[dict] | None = None) -> dict:
    return {
        "group": name,
        "packages": [f"ai.speckitty.charter.{name}"],
        "excludes": [],
        # 100 mutants, 10 of them uncovered: detected, survived and strength follow from the score.
        "mutants": 100,
        "detected": int(float(score)),
        "survived": 90 - int(float(score)),
        "noCoverage": 10,
        "timedOut": 1,
        "score": float(score),
        "strength": int(float(score)) * 1000 // 90 / 10,
        "floor": floor,
        # 100 mutants: the floor in percent is the fewest detected mutants that pass.
        "marginMutants": int(float(score)) - floor,
        "marginPoints": round(float(score) - floor, 1),
        "passed": passed,
        "topClasses": top or [],
    }


def summary(project: str, *groups: dict) -> str:
    return json.dumps({"project": project, "mutants": sum(g["mutants"] for g in groups), "groups": list(groups)})


class RenderTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, module: str, text: str) -> None:
        path = self.root / "service" / module / ms.SUMMARY_PATH
        path.parent.mkdir(parents=True)
        path.write_text(text, encoding="utf-8")

    def write_over(self, module: str, text: str) -> None:
        path = self.root / "service" / module / ms.SUMMARY_PATH
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def row(self, text: str, start: str) -> str:
        rows = [line for line in text.splitlines() if line.startswith(start)]
        self.assertEqual(len(rows), 1, f"one row starting {start!r} in:\n{text}")
        return rows[0]

    def all_green(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True)))
        self.write("missions", summary(":missions", group("missions", score="75.0", floor=70, passed=True)))
        self.write(
            "missions-edge",
            summary(":missions-edge", group("missions-edge", score="88.0", floor=85, passed=True)),
        )
        self.write("profiles", PROFILES)
        self.write(
            "profiles-edge",
            summary(":profiles-edge", group("profiles-edge", score="88.0", floor=85, passed=True)),
        )
        self.write(
            "packs",
            summary(
                ":packs",
                group("domain+application", score="80.0", floor=75, passed=True),
                group("infra.storage", score="70.0", floor=65, passed=True),
            ),
        )
        self.write("activations", summary(":activations", group("activations", score="91.4", floor=85, passed=True)))

    def test_a_real_summary_renders_its_figures(self) -> None:
        self.write("profiles", PROFILES)
        row = self.row(ms.render(self.root, MODULES), "| `:profiles` profiles |")
        self.assertEqual(
            row,
            "| `:profiles` profiles | 66 | 53.0 % | 94.5 % | 29 | 0 | 50 % | +3.0 pts, 2 | ✅ pass |"
            " `AgentProfile` (2 survived, 29 uncovered) |",
        )

    def test_every_group_green_says_so(self) -> None:
        self.all_green()
        text = ms.render(self.root, MODULES)
        self.assertIn("✅ Every group is at or above its floor.", text)
        self.assertNotIn("FAIL", text)
        self.assertNotIn("no report", text)
        self.assertIn("| `:packs` domain+application |", text)
        self.assertIn("| `:packs` infra.storage |", text)

    def test_a_failing_group_is_marked_and_named(self) -> None:
        self.all_green()
        (self.root / "service" / "packs" / ms.SUMMARY_PATH).write_text(
            summary(
                ":packs",
                group("domain+application", score="80.0", floor=75, passed=True),
                group("infra.storage", score="61.2", floor=65, passed=False),
            ),
            encoding="utf-8",
        )
        text = ms.render(self.root, MODULES)
        self.assertIn("❌ **FAIL**", self.row(text, "| `:packs` infra.storage |"))
        self.assertIn("✅ pass", self.row(text, "| `:packs` domain+application |"))
        self.assertIn("**❌ Not every group is at its floor:** `:packs` infra.storage.", text)

    def test_a_module_without_a_summary_shows_no_report(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True)))
        text = ms.render(self.root, MODULES)
        for module in ("missions", "missions-edge", "profiles", "profiles-edge", "packs", "activations"):
            self.assertIn("⚠️ **no report**", self.row(text, f"| `:{module}` |"))
        self.assertIn(
            "`:activations` (no report), `:missions` (no report), `:missions-edge` (no report), `:packs` (no report),"
            " `:profiles` (no report), `:profiles-edge` (no report)",
            text,
        )

    def test_no_summary_at_all_lists_every_listed_module(self) -> None:
        text = ms.render(self.root, MODULES)
        modules = [line.split("`")[1] for line in text.splitlines() if line.startswith("| `:")]
        self.assertEqual(
            modules, [":activations", ":kernel", ":missions", ":missions-edge", ":packs", ":profiles", ":profiles-edge"]
        )

    def test_a_module_the_build_lists_gets_a_row_though_no_one_wrote_its_name_here(self) -> None:
        # #388: the 2026-11-11 nightly had no :missions-edge row, because the list was kept by hand.
        self.all_green()
        text = ms.render(self.root, MODULES + ["service/brand-new"])
        self.assertIn("⚠️ **no report**", self.row(text, "| `:brand-new` |"))
        self.assertIn("**❌ Not every group is at its floor:** `:brand-new` (no report).", text)

    def test_without_the_module_list_the_summary_says_so_and_is_not_green(self) -> None:
        self.all_green()
        text = ms.render(self.root, None)
        self.assertIn("the module list (`./gradlew mutationModules`) was not written", text)
        self.assertNotIn("✅ Every group is at or above its floor.", text)
        self.assertIn("✅ pass", self.row(text, "| `:kernel` kernel |"))

    def test_read_modules_reads_one_directory_per_line(self) -> None:
        listed = self.root / "modules.txt"
        listed.write_text("service/kernel\n\nservice/packs\n", encoding="utf-8")
        self.assertEqual(ms.read_modules(listed), ["service/kernel", "service/packs"])
        self.assertIsNone(ms.read_modules(self.root / "absent.txt"))
        listed.write_text("\n", encoding="utf-8")
        self.assertIsNone(ms.read_modules(listed))

    def test_read_listing_reads_each_modules_floor_groups(self) -> None:
        listed = self.root / "modules.txt"
        listed.write_text("service/kernel\tkernel\n\nservice/packs\tdomain+application\tinfra.storage\n", encoding="utf-8")
        self.assertEqual(
            ms.read_listing(listed),
            {"service/kernel": ["kernel"], "service/packs": ["domain+application", "infra.storage"]},
        )
        self.assertEqual(ms.read_modules(listed), ["service/kernel", "service/packs"])

    def write_shard(self, module: str, shard: str, text: str) -> None:
        path = self.root / "service" / module / ms.SUMMARY_PATH.parent / f"floors-{shard}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def test_a_modules_shards_render_as_one_module(self) -> None:
        # #461: :packs runs as two shards, each writing floors-<shard>.json.
        self.write_shard(
            "packs", "domain-application", summary(":packs", group("domain+application", score="91.0", floor=85, passed=True))
        )
        self.write_shard(
            "packs", "infra.storage", summary(":packs", group("infra.storage", score="90.0", floor=85, passed=True))
        )
        text = ms.render(
            self.root, ["service/packs"], expected={"service/packs": ["domain+application", "infra.storage"]}
        )
        self.assertIn("✅ pass", self.row(text, "| `:packs` domain+application |"))
        self.assertIn("✅ pass", self.row(text, "| `:packs` infra.storage |"))
        self.assertIn("**✅ Every group is at or above its floor.**", text)

    def test_a_group_whose_shard_wrote_nothing_shows_no_report(self) -> None:
        # One shard of :packs timed out: its groups must not vanish from a table that reads green.
        self.write_shard(
            "packs", "domain-application", summary(":packs", group("domain+application", score="91.0", floor=85, passed=True))
        )
        text = ms.render(
            self.root, ["service/packs"], expected={"service/packs": ["domain+application", "infra.storage"]}
        )
        self.assertIn("⚠️ **no report**", self.row(text, "| `:packs` infra.storage |"))
        self.assertIn("**❌ Not every group is at its floor:** `:packs` infra.storage (no report).", text)

    def test_one_unreadable_shard_makes_the_module_unreadable(self) -> None:
        self.write_shard(
            "packs", "domain-application", summary(":packs", group("domain+application", score="91.0", floor=85, passed=True))
        )
        self.write_shard("packs", "infra.storage", "{not json")
        text = ms.render(self.root, ["service/packs"])
        self.assertIn(ms.UNREADABLE, self.row(text, "| `:packs` |"))

    def test_the_margin_and_the_timed_out_count_are_shown(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="64.9", floor=65, passed=False)))
        row = self.row(ms.render(self.root, MODULES), "| `:kernel` kernel |")
        self.assertIn("| 1 | 65 % | -0.1 pts, -1 | ❌ **FAIL** |", row)

    def test_a_group_without_its_margin_figures_is_invalid_and_fails(self) -> None:
        for key in ("timedOut", "marginMutants", "marginPoints"):
            with self.subTest(missing=key):
                invalid = group("kernel", score="72.4", floor=65, passed=True)
                del invalid[key]
                (self.root / "service" / "kernel" / ms.SUMMARY_PATH).unlink(missing_ok=True)
                self.write_over("kernel", summary(":kernel", invalid))
                text = ms.render(self.root, MODULES)
                self.assertIn("❌ **FAIL**", self.row(text, "| `:kernel` kernel |"))
                self.assertNotIn("✅ Every group is at or above its floor.", text)

    def test_a_boolean_is_not_a_margin(self) -> None:
        invalid = group("kernel", score="72.4", floor=65, passed=True)
        invalid["marginMutants"] = True
        self.write("kernel", summary(":kernel", invalid))
        self.assertIn("| ? | ❌ **FAIL** |", self.row(ms.render(self.root, MODULES), "| `:kernel` kernel |"))

    def test_an_unreadable_summary_is_not_taken_for_green(self) -> None:
        self.all_green()
        (self.root / "service" / "kernel" / ms.SUMMARY_PATH).write_text("{ not json", encoding="utf-8")
        text = ms.render(self.root, MODULES)
        self.assertIn("⚠️ **unreadable report**", self.row(text, "| `:kernel` |"))
        self.assertIn("`:kernel` (unreadable report)", text)

    def test_an_unlisted_module_with_a_summary_is_shown_after_the_listed_ones(self) -> None:
        self.all_green()
        self.write("extra", summary(":extra", group("extra", score="90.0", floor=50, passed=True)))
        text = ms.render(self.root, MODULES)
        modules = [line.split("`")[1] for line in text.splitlines() if line.startswith("| `:")]
        self.assertEqual(
            modules,
            [
                ":activations",
                ":kernel",
                ":missions",
                ":missions-edge",
                ":packs",
                ":packs",
                ":profiles",
                ":profiles-edge",
                ":extra",
            ],
        )

    def test_a_pipe_in_a_name_does_not_break_the_row(self) -> None:
        self.write("kernel", summary(":kernel", group("a|b", score="72.4", floor=65, passed=True)))
        row = self.row(ms.render(self.root, MODULES), "| `:kernel` a\\|b |")
        self.assertEqual(row.replace("\\|", "").count("|"), 11)

    def test_uncovered_only_classes_omit_the_zero(self) -> None:
        top = [{"class": "a.b.RefusalCode", "survived": 0, "noCoverage": 14}, {"class": "a.b.Pep440Range", "survived": 22, "noCoverage": 0}]
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True, top=top)))
        row = self.row(ms.render(self.root, MODULES), "| `:kernel` kernel |")
        self.assertIn("`RefusalCode` (14 uncovered)<br>`Pep440Range` (22 survived)", row)

    def test_a_module_with_no_group_shows_no_report(self) -> None:
        self.write("kernel", json.dumps({"project": ":kernel", "mutants": 0, "groups": []}))
        text = ms.render(self.root, MODULES)
        self.assertIn("⚠️ **no report**", self.row(text, "| `:kernel` |"))
        self.assertIn("`:kernel` (no group)", text)

    def test_a_group_without_a_verdict_is_a_failure(self) -> None:
        unjudged = group("kernel", score="72.4", floor=65, passed=True)
        del unjudged["passed"]
        self.write("kernel", summary(":kernel", unjudged))
        text = ms.render(self.root, MODULES)
        self.assertIn("❌ **FAIL**", self.row(text, "| `:kernel` kernel |"))
        verdict = next(line for line in text.splitlines() if line.startswith("**❌ Not every group is at its floor:**"))
        self.assertIn("`:kernel` kernel", verdict)

    def test_a_group_that_is_not_an_object_makes_the_summary_unreadable(self) -> None:
        self.write("kernel", json.dumps({"project": ":kernel", "mutants": 1, "groups": ["kernel"]}))
        text = ms.render(self.root, MODULES)
        self.assertIn("⚠️ **unreadable report**", self.row(text, "| `:kernel` |"))
        self.assertIn("`:kernel` (unreadable report)", text)

    def test_the_kotlin_summary_has_exactly_the_keys_this_reader_knows(self) -> None:
        data = json.loads(KOTLIN_FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(set(data), SUMMARY_KEYS)
        self.assertEqual(len(data["groups"]), 2)
        for entry in data["groups"]:
            self.assertEqual(set(entry), GROUP_KEYS)
            for gaps in entry["topClasses"]:
                self.assertEqual(set(gaps), TOP_CLASS_KEYS)

    def test_the_kotlin_summary_renders_its_figures(self) -> None:
        self.write("packs", KOTLIN_FIXTURE.read_text(encoding="utf-8"))
        text = ms.render(self.root, MODULES)
        self.assertEqual(
            self.row(text, "| `:packs` domain+application |"),
            "| `:packs` domain+application | 10 | 90.0 % | 90.0 % | 0 | 0 | 85 % | +5.0 pts, 0 | ✅ pass | `Pack` (1 survived) |",
        )
        self.assertEqual(
            self.row(text, "| `:packs` infra.storage |"),
            "| `:packs` infra.storage | 4 | 75.0 % | 100.0 % | 1 | 0 | 85 % | -10.0 pts, -1 | ❌ **FAIL** |"
            " `FileStore` (1 uncovered) |",
        )

    def test_tests_that_fail_unmutated_are_quoted_from_the_log(self) -> None:
        # #461: the job log of a red nightly expired before anyone read which test failed; the summary keeps it.
        self.all_green()
        log = self.root / "pitest.log"
        log.write_text(
            "> Task :packs:pitest\n"
            "8:44:04 PM PIT >> SEVERE : Description [testClass=a.b.PackFileSetExactnessTest, name=[method:x()]]"
            " did not pass without mutation.\n"
            "8:44:05 PM PIT >> INFO : MINION : unrelated chatter\n"
            "1 tests did not pass without mutation when calculating line coverage. Mutation testing requires a green"
            " suite.\n",
            encoding="utf-8",
        )
        text = ms.render(self.root, MODULES, ms.unmutated_failures(log))
        self.assertIn("### Tests that fail with no mutation applied", text)
        self.assertIn("- `8:44:04 PM PIT >> SEVERE : Description [testClass=a.b.PackFileSetExactnessTest", text)
        self.assertIn("- `1 tests did not pass without mutation when calculating line coverage.", text)
        self.assertNotIn("unrelated chatter", text)

    def test_a_clean_or_missing_log_adds_nothing(self) -> None:
        log = self.root / "pitest.log"
        log.write_text("BUILD SUCCESSFUL\n", encoding="utf-8")
        self.assertEqual(ms.unmutated_failures(log), [])
        self.assertEqual(ms.unmutated_failures(self.root / "absent.log"), [])
        self.assertNotIn("Tests that fail", ms.render(self.root, MODULES, []))

    def test_a_backtick_in_a_quoted_line_does_not_break_out_of_the_code_span(self) -> None:
        text = ms.render(self.root, MODULES, ["x `y` did not pass without mutation"])
        self.assertIn("- `x 'y' did not pass without mutation`", text)

    def test_main_appends_to_the_step_summary_and_exits_non_zero_only_for_a_group_no_one_reported(self) -> None:
        step_summary = self.root / "step-summary.md"
        step_summary.write_text("earlier step\n", encoding="utf-8")
        self.write("kernel", summary(":kernel", group("kernel", score="40.0", floor=65, passed=False)))
        listed = self.root / "modules.txt"
        listed.write_text("service/kernel\tkernel\n", encoding="utf-8")
        args = ["--root", str(self.root), "--modules", str(listed)]
        with mock.patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(step_summary)}), redirect_stdout(io.StringIO()) as out:
            status = ms.main(args)
        # A group below its floor is the pitest step's failure: the summary only reports it.
        self.assertEqual(status, 0)
        written = step_summary.read_text(encoding="utf-8")
        self.assertTrue(written.startswith("earlier step\n## Mutation floors\n"))
        self.assertEqual(written.removeprefix("earlier step\n"), out.getvalue())
        # A group the build lists that no shard reported may have gone unmutated with every job green: the job fails.
        listed.write_text("service/kernel\tkernel\tkernel.extra\n", encoding="utf-8")
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            self.assertEqual(ms.main(args), 1)
        # So does a listed module whose report is unreadable.
        (self.root / "service/kernel/build/reports/mutation-floors/floors.json").write_text("{", encoding="utf-8")
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            self.assertEqual(ms.main(args), 1)

    def test_main_holds_each_module_to_the_groups_the_build_lists(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True)))
        listed = self.root / "modules.txt"
        listed.write_text("service/kernel\tkernel\tkernel.extra\n", encoding="utf-8")
        with redirect_stdout(io.StringIO()) as out:
            ms.main(["--root", str(self.root), "--modules", str(listed)])
        self.assertIn("⚠️ **no report**", self.row(out.getvalue(), "| `:kernel` kernel.extra |"))


if __name__ == "__main__":
    unittest.main()
