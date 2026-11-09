"""The nightly mutation summary renders every module's floors.json, and says so when one is missing or red.

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
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mutation_summary as ms  # noqa: E402

# Written by MutationFloors.toJson, through build-logic's MutationFloorsTest (the key contract).
KOTLIN_FIXTURE = Path(__file__).resolve().parent / "fixtures" / "floors.json"
SUMMARY_KEYS = {"project", "mutants", "groups"}
GROUP_KEYS = {
    "group", "packages", "excludes", "mutants", "detected", "survived", "noCoverage", "score", "strength", "floor",
    "passed", "topClasses",
}
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
      "score": 53.0,
      "strength": 94.5,
      "floor": 50,
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
        "mutants": 100,
        "detected": 60,
        "survived": 30,
        "noCoverage": 10,
        "score": float(score),
        "strength": 66.6,
        "floor": floor,
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

    def row(self, text: str, start: str) -> str:
        rows = [line for line in text.splitlines() if line.startswith(start)]
        self.assertEqual(len(rows), 1, f"one row starting {start!r} in:\n{text}")
        return rows[0]

    def all_green(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True)))
        self.write("missions", summary(":missions", group("missions", score="75.0", floor=70, passed=True)))
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
        row = self.row(ms.render(self.root), "| `:profiles` profiles |")
        self.assertEqual(
            row,
            "| `:profiles` profiles | 66 | 53.0 % | 94.5 % | 29 | 50 % | ✅ pass | `AgentProfile` (2 survived, 29 uncovered) |",
        )

    def test_every_group_green_says_so(self) -> None:
        self.all_green()
        text = ms.render(self.root)
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
        text = ms.render(self.root)
        self.assertIn("❌ **FAIL**", self.row(text, "| `:packs` infra.storage |"))
        self.assertIn("✅ pass", self.row(text, "| `:packs` domain+application |"))
        self.assertIn("**❌ Not every group is at its floor:** `:packs` infra.storage.", text)

    def test_a_module_without_a_summary_shows_no_report(self) -> None:
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True)))
        text = ms.render(self.root)
        for module in ("missions", "profiles", "profiles-edge", "packs", "activations"):
            self.assertIn("⚠️ **no report**", self.row(text, f"| `:{module}` |"))
        self.assertIn(
            "`:missions` (no report), `:profiles` (no report), `:profiles-edge` (no report), `:packs` (no report),"
            " `:activations` (no report)",
            text,
        )

    def test_no_summary_at_all_lists_every_expected_module(self) -> None:
        text = ms.render(self.root)
        modules = [line.split("`")[1] for line in text.splitlines() if line.startswith("| `:")]
        self.assertEqual(modules, [":kernel", ":missions", ":profiles", ":profiles-edge", ":packs", ":activations"])

    def test_an_unreadable_summary_is_not_taken_for_green(self) -> None:
        self.all_green()
        (self.root / "service" / "kernel" / ms.SUMMARY_PATH).write_text("{ not json", encoding="utf-8")
        text = ms.render(self.root)
        self.assertIn("⚠️ **unreadable report**", self.row(text, "| `:kernel` |"))
        self.assertIn("`:kernel` (unreadable report)", text)

    def test_an_unexpected_module_is_listed_after_the_expected_ones(self) -> None:
        self.all_green()
        self.write("extra", summary(":extra", group("extra", score="90.0", floor=50, passed=True)))
        text = ms.render(self.root)
        modules = [line.split("`")[1] for line in text.splitlines() if line.startswith("| `:")]
        self.assertEqual(
            modules, [":kernel", ":missions", ":profiles", ":profiles-edge", ":packs", ":packs", ":activations", ":extra"]
        )

    def test_a_pipe_in_a_name_does_not_break_the_row(self) -> None:
        self.write("kernel", summary(":kernel", group("a|b", score="72.4", floor=65, passed=True)))
        row = self.row(ms.render(self.root), "| `:kernel` a\\|b |")
        self.assertEqual(row.replace("\\|", "").count("|"), 9)

    def test_uncovered_only_classes_omit_the_zero(self) -> None:
        top = [{"class": "a.b.RefusalCode", "survived": 0, "noCoverage": 14}, {"class": "a.b.Pep440Range", "survived": 22, "noCoverage": 0}]
        self.write("kernel", summary(":kernel", group("kernel", score="72.4", floor=65, passed=True, top=top)))
        row = self.row(ms.render(self.root), "| `:kernel` kernel |")
        self.assertIn("`RefusalCode` (14 uncovered)<br>`Pep440Range` (22 survived)", row)

    def test_a_module_with_no_group_shows_no_report(self) -> None:
        self.write("kernel", json.dumps({"project": ":kernel", "mutants": 0, "groups": []}))
        text = ms.render(self.root)
        self.assertIn("⚠️ **no report**", self.row(text, "| `:kernel` |"))
        self.assertIn("`:kernel` (no group)", text)

    def test_a_group_without_a_verdict_is_a_failure(self) -> None:
        unjudged = group("kernel", score="72.4", floor=65, passed=True)
        del unjudged["passed"]
        self.write("kernel", summary(":kernel", unjudged))
        text = ms.render(self.root)
        self.assertIn("❌ **FAIL**", self.row(text, "| `:kernel` kernel |"))
        self.assertIn("**❌ Not every group is at its floor:** `:kernel` kernel", text)

    def test_a_group_that_is_not_an_object_makes_the_summary_unreadable(self) -> None:
        self.write("kernel", json.dumps({"project": ":kernel", "mutants": 1, "groups": ["kernel"]}))
        text = ms.render(self.root)
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
        text = ms.render(self.root)
        self.assertEqual(
            self.row(text, "| `:packs` domain+application |"),
            "| `:packs` domain+application | 10 | 90.0 % | 90.0 % | 0 | 85 % | ✅ pass | `Pack` (1 survived) |",
        )
        self.assertEqual(
            self.row(text, "| `:packs` infra.storage |"),
            "| `:packs` infra.storage | 4 | 75.0 % | 100.0 % | 1 | 85 % | ❌ **FAIL** | `FileStore` (1 uncovered) |",
        )

    def test_main_appends_to_the_step_summary_and_exits_zero_even_when_red(self) -> None:
        step_summary = self.root / "step-summary.md"
        step_summary.write_text("earlier step\n", encoding="utf-8")
        with mock.patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(step_summary)}), redirect_stdout(io.StringIO()) as out:
            status = ms.main(["--root", str(self.root)])
        self.assertEqual(status, 0)
        written = step_summary.read_text(encoding="utf-8")
        self.assertTrue(written.startswith("earlier step\n## Mutation floors\n"))
        self.assertEqual(written.removeprefix("earlier step\n"), out.getvalue())


if __name__ == "__main__":
    unittest.main()
