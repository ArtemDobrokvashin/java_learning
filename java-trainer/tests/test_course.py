"""Keep authored course specifications and shipped JSON in sync."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from course_builder import F, P, build_module  # noqa: E402
from lesson_notes import NOTES, note_for  # noqa: E402
from lesson_extensions import EXTRAS  # noqa: E402
from guided_lessons import GUIDES, guide_for  # noqa: E402
from practice_guides import PRIMERS, primer_for  # noqa: E402
from modules_04_08 import SPECS as FIRST  # noqa: E402
from modules_09_13 import SPECS as SECOND  # noqa: E402
from modules_14_18 import SPECS as THIRD  # noqa: E402
from modules_19_22 import SPECS as FOURTH  # noqa: E402


def reference_lab_answer(lab_id: str, data: str) -> str:
    """Independent reference for the small published lab contracts."""
    rows = data.splitlines()
    n = int(rows[0])
    if lab_id == "lab-array-report":
        values = [int(value) for value in " ".join(rows[1:]).split()]
        assert len(values) == n
        return f"{min(values)} {max(values)} {sum(values)}"
    if lab_id == "lab-user-lookup":
        users = {int(parts[0]): (parts[1], parts[2] == "true")
                 for parts in (row.split(";") for row in rows[1:n + 1])}
        user = users.get(int(rows[n + 1]))
        return "NOT_FOUND" if not user else user[0] if user[1] else "INACTIVE"
    if lab_id == "lab-import-report":
        ages = []
        for row in rows[1:n + 1]:
            parts = row.split(",")
            if len(parts) != 2 or not parts[0].strip():
                continue
            try:
                age = int(parts[1].strip())
            except ValueError:
                continue
            if 0 <= age <= 120:
                ages.append(age)
        return f"{len(ages)} {sum(ages)}"
    if lab_id == "lab-order-totals":
        totals = {}
        for row in rows[1:n + 1]:
            user_id, amount = (int(part) for part in row.split())
            totals[user_id] = totals.get(user_id, 0) + amount
        threshold = int(rows[n + 1])
        return ",".join(f"{id}:{total}" for id, total in sorted(totals.items())
                        if total >= threshold) or "-"
    if lab_id == "lab-rest-router":
        results = []
        for row in rows[1:n + 1]:
            method, path = row.split(" ", 1)
            if method == "GET" and path == "/health":
                results.append("200 OK")
            elif method == "POST" and path == "/users":
                results.append("201 CREATED")
            elif method == "GET" and path.startswith("/users/"):
                identifier = path.removeprefix("/users/")
                valid = identifier.isascii() and identifier.isdecimal() and 0 < int(identifier or 0) <= 2**63 - 1
                results.append(f"200 USER {int(identifier)}" if valid else "404 NOT_FOUND")
            else:
                results.append("404 NOT_FOUND")
        return "\n".join(results)
    raise ValueError(lab_id)


class CourseSourceTests(unittest.TestCase):
    def test_generated_json_matches_authored_sources(self):
        data_dir = ROOT / "frontend" / "data"
        index = json.loads((data_dir / "modules.json").read_text(encoding="utf-8"))
        specs = {**FIRST, **SECOND, **THIRD, **FOURTH}
        self.assertEqual(set(specs), set(range(4, 23)))
        self.assertEqual(index["version"], 9)
        for number, spec in specs.items():
            with self.subTest(module=number):
                shipped = json.loads((data_dir / f"module-{number:02d}.json").read_text(encoding="utf-8"))
                self.assertEqual(build_module(number, spec, index), shipped)
                self.assertEqual(index["modules"][number - 1]["contentStatus"], "ready")

    def test_parsons_variations_are_authored_permutations_of_existing_lines(self):
        variations = {}
        for number in range(1, 23):
            content = json.loads((ROOT / "frontend" / "data" / f"module-{number:02d}.json")
                                 .read_text(encoding="utf-8"))
            activities = content["lessons"] + [content["practice"]]
            for activity in activities:
                for task in activity["tasks"]:
                    if task["type"] != "parsons" or "alternativeOrders" not in task:
                        continue
                    ids = [line["id"] for line in task["solution"]]
                    self.assertEqual(len(set(ids)), len(ids))
                    for alternative in task["alternativeOrders"]:
                        self.assertEqual(set(alternative), set(ids), task["id"])
                        self.assertEqual(len(alternative), len(ids), task["id"])
                        self.assertNotEqual(alternative, ids, task["id"])
                    variations[task["id"]] = len(task["alternativeOrders"])
        self.assertEqual(variations, {"m05-p04": 1, "m14-p04": 1, "m17-p04": 1,
                                      "m18-p04": 1, "m22-p04": 23})
        with self.assertRaises(AssertionError):
            P("Неверный вариант", [("first", 0), ("second", 1), ("third", 0)],
              "hint", "explanation", alternative_orders=[("a", "a", "c")])

    def test_fill_authoring_supports_documented_equivalents_without_accepting_distractors(self):
        task = F("Укажите название", ["name = ", ";"], "true", ["true", "false"],
                 "hint", "explanation", accepted=["true"])
        self.assertEqual(task["blanks"][0]["accepted"], ["true"])
        with self.assertRaises(AssertionError):
            F("Неизвестный вариант", ["x = ", ";"], "true", ["true", "false"],
              "hint", "explanation", accepted=["true", "maybe"])
        text = F("Число", ["age = ", ";"], "25", [], "hint", "explanation",
                 kind="text", validation={"kind": "integerRange", "min": 0, "max": 120})
        self.assertEqual(text["blanks"][0]["validation"]["max"], 120)

    def test_labs_have_public_examples_and_server_owned_multiple_cases(self):
        data_dir = ROOT / "frontend" / "data"
        labs = json.loads((data_dir / "labs.json").read_text(encoding="utf-8"))["labs"]
        cases = json.loads((ROOT / "private" / "lab_cases.json").read_text(encoding="utf-8"))
        self.assertEqual(len(labs), 5)
        self.assertEqual({lab["id"] for lab in labs}, set(cases))
        for lab in labs:
            with self.subTest(lab=lab["id"]):
                self.assertEqual((lab["type"], lab["mode"]), ("code", "run"))
                self.assertTrue(lab["starterCode"].startswith("import java."))
                self.assertIn("class Main", lab["starterCode"])
                self.assertIn("TODO", lab["starterCode"])
                self.assertTrue(lab["goal"] and lab["description"] and lab["hint"])
                self.assertTrue(all(1 <= id <= 22 for id in lab["prerequisites"]))
                self.assertNotIn("testCases", lab)
                self.assertNotIn("expectedOutput", lab)
                self.assertEqual(len(cases[lab["id"]]), 4)
                self.assertEqual(len({case["input"] for case in cases[lab["id"]]}), 4)
                self.assertEqual(lab["sampleInput"], cases[lab["id"]][0]["input"])
                self.assertEqual(lab["sampleOutput"], cases[lab["id"]][0]["expectedOutput"])
                for case in cases[lab["id"]]:
                    self.assertEqual(reference_lab_answer(lab["id"], case["input"]), case["expectedOutput"])

    def test_every_lesson_has_a_specific_outcome_example_and_mistake(self):
        data_dir = ROOT / "frontend" / "data"
        index = json.loads((data_dir / "modules.json").read_text(encoding="utf-8"))
        self.assertEqual(set(NOTES), set(range(1, 23)))
        self.assertEqual(sum(map(len, NOTES.values())), 99)
        self.assertEqual({number: len(notes) for number, notes in NOTES.items()},
                         {number: len(notes) for number, notes in EXTRAS.items()})
        expanded = set()
        for metadata in index["modules"]:
            number = metadata["id"]
            content = json.loads((data_dir / metadata["contentFile"]).read_text(encoding="utf-8"))
            self.assertEqual(len(content["lessons"]), len(NOTES[number]))
            for position, lesson in enumerate(content["lessons"], 1):
                with self.subTest(lesson=lesson["id"]):
                    self.assertEqual(lesson["theory"]["objective"], note_for(number, position)["objective"])
                    self.assertEqual(lesson["theory"]["walkthrough"], note_for(number, position)["walkthrough"])
                    self.assertEqual(lesson["theory"]["pitfall"], note_for(number, position)["pitfall"])
                    for field in ("deepDive", "useCase"):
                        text = lesson["theory"][field]
                        self.assertEqual(text, note_for(number, position)[field])
                        self.assertTrue(90 <= len(text) <= 420)
                        self.assertNotIn(text, expanded, "distinct explanation per lesson")
                        expanded.add(text)
        self.assertEqual(len(expanded), 198)

    def test_every_activity_teaches_the_actions_used_by_its_code_tasks(self):
        """Guides are authored, shipped, task-linked, and not substituted answers."""
        data_dir = ROOT / "frontend" / "data"
        self.assertEqual(set(GUIDES), set(PRIMERS), set(range(1, 23)))
        examples = set()
        task_ids = set()
        for number in range(1, 23):
            content = json.loads((data_dir / f"module-{number:02d}.json").read_text(encoding="utf-8"))
            self.assertEqual(len(GUIDES[number]), len(content["lessons"]))
            for position, lesson in enumerate(content["lessons"]):
                with self.subTest(lesson=lesson["id"]):
                    guide = lesson["theory"]["guide"]
                    self.assertEqual(guide, guide_for(number, position))
                    self.assertEqual(guide, note_for(number, position + 1)["guide"])
                    self.assertGreaterEqual(len(guide["steps"]), 2)
                    self.assertTrue(all(guide[field] for field in ("why", "result", "bridge")))
                    example = guide["example"]
                    self.assertNotEqual(example["code"], lesson["theory"]["code"],
                                        "a second example must actually teach another case")
                    self.assertNotIn(example["code"], examples, "unique example per lesson")
                    examples.add(example["code"])
                    if example["runnable"]:
                        self.assertIn("public class Main {", example["code"])
                        self.assertIn("public static void main(String[] args)", example["code"])
                        self.assertTrue(example["stdout"])
                    else:
                        self.assertNotIn("stdout", example, "fragments cannot promise standalone output")
            primer = content["practice"]["primer"]
            self.assertEqual(primer, primer_for(number))
            self.assertGreaterEqual(len(primer["steps"]), 2)
            self.assertTrue(primer["example"]["runnable"])
            self.assertTrue(primer["example"]["stdout"])
            code_tasks = [task for task in content["practice"]["tasks"] if task["type"] == "code"]
            self.assertEqual([hint["taskId"] for hint in primer["hints"]],
                             [task["id"] for task in code_tasks])
            self.assertTrue(all(len(hint["text"]) > 65 for hint in primer["hints"]))
            task_ids.update(task["id"] for task in code_tasks)
        self.assertEqual(len(examples), 99)
        self.assertEqual(len(task_ids), 44)
        first = json.loads((data_dir / "module-01.json").read_text(encoding="utf-8"))
        lesson = first["lessons"][0]["theory"]
        self.assertIn("println", lesson["objective"])
        for line in ("public class Main {", "public static void main(String[] args)",
                     "int age =", "String name =", "System.out.println(age);",
                     "System.out.println(name);"):
            self.assertIn(line, lesson["guide"]["example"]["code"])
        self.assertEqual(lesson["guide"]["example"]["stdout"], "16\nМира\ntrue")
        self.assertIn('println("age")', lesson["guide"]["steps"][2])
        self.assertIn("System.out.println", first["practice"]["primer"]["why"])
        third = json.loads((data_dir / "module-03.json").read_text(encoding="utf-8"))
        self.assertIn("int best = values[0]", third["practice"]["primer"]["example"]["code"])
        self.assertIn("if (value > best) best = value", third["practice"]["primer"]["example"]["code"])

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK not installed")
    def test_complete_worked_examples_compile_and_print_documented_output(self):
        """Run all complete examples with JDK 17+; fragments need their noted context."""
        samples = [(f"m{number:02d}-l{position:02d}", guide["example"])
                   for number, guides in GUIDES.items()
                   for position, guide in enumerate(guides, 1) if guide["example"]["runnable"]]
        samples += [(f"m{number:02d}-practice", primer["example"])
                    for number, primer in PRIMERS.items()]
        self.assertEqual(len(samples), 36)
        for name, sample in samples:
            with self.subTest(example=name), tempfile.TemporaryDirectory() as directory:
                Path(directory, "Main.java").write_text(sample["code"], encoding="utf-8")
                compiled = subprocess.run(["javac", "-encoding", "UTF-8", "Main.java"],
                                          cwd=directory, capture_output=True, text=True, timeout=20)
                self.assertEqual(compiled.returncode, 0, compiled.stderr)
                result = subprocess.run(["java", "-cp", directory, "Main"],
                                        cwd=directory, capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout.replace("\r\n", "\n").rstrip("\n"), sample["stdout"])


if __name__ == "__main__":
    unittest.main()
