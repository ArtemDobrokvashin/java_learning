"""Shared browser/IDE profile: migration, drafts, idempotency and local-only API."""

import os
import tempfile
from datetime import datetime, timedelta
import unittest
from pathlib import Path
from unittest.mock import patch

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server  # noqa: E402
import shared_progress  # noqa: E402


class SharedProgressTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.env = patch.dict(os.environ, {"JAVADA_PROFILE_DIR": self.folder.name,
                                        "TRAINER_HOST": "127.0.0.1", "TRAINER_DISABLE_JAVA": "0"})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.client = server.create_app().test_client()

    def old_browser(self, **extra):
        return {"version": 1, "xp": 70, "awardedTasks": ["m01-p06"],
                "completedLessons": ["m01-l01"], "completedPractices": [],
                "attempts": {"m01-p06": {"correct": True, "mistakes": 1}},
                "theme": "dark", **extra}

    def compile(self, task_id, *, correct=True):
        answer = server.code_tasks()[task_id]["expectedOutput"] if correct else "wrong output"
        with patch("server.shutil.which", side_effect=lambda name: "/fake/" + name), \
             patch("server.run_command", side_effect=[server.CommandResult(0, "", ""),
                                                      server.CommandResult(0, answer, "")]):
            return self.client.post("/api/compile", json={
                "code": "public class Main { public static void main(String[] args) {} }",
                "taskType": "run", "taskId": task_id, "recordProgress": True})

    def test_old_browser_xp_draft_and_task_ids_migrate_without_reawarding(self):
        old = self.old_browser()
        first = self.client.post("/api/progress/merge", json={"source": "browser", "state": old,
            "drafts": {"m01-p07": {"code": "work in progress", "updatedAt": 123}}}).json
        self.assertEqual((first["state"]["xp"], first["state"]["legacyXp"]), (70, 70))
        self.assertIn("m01-p06", first["state"]["legacyAwardedTasks"])
        self.assertEqual(first["state"]["completedLessons"], ["m01-l01"])
        self.assertEqual(first["drafts"]["m01-p07"]["code"], "work in progress")
        self.assertEqual(self.compile("m01-p06").json["progress"]["state"]["xp"], 70)
        second = self.compile("m01-p07").json["progress"]["state"]
        self.assertEqual(second["xp"], 120)
        self.assertEqual(second["xpLedger"], {"task:m01-p07": 50})
        # A stale browser copy may reconnect later. The server must union IDs,
        # keep new awards, and never interpret old XP as another 70 points.
        again = self.client.post("/api/progress/merge", json={"source": "browser", "state": old}).json
        self.assertEqual(again["state"]["xp"], 120)
        self.assertEqual(self.compile("m01-p07").json["progress"]["state"]["xp"], 120)
        self.assertEqual(self.client.get("/api/progress").json["state"]["theme"], "dark")

    def test_plugin_can_award_before_legacy_browser_is_imported(self):
        self.assertEqual(self.compile("m01-p06").json["progress"]["state"]["xp"], 50)
        browser = self.client.post("/api/progress/merge", json={
            "source": "browser", "state": self.old_browser()}).json["state"]
        self.assertEqual(browser["xp"], 70)
        self.assertEqual(browser["xpLedger"]["task:m01-p06"], 50)
        self.assertIn("m01-p06", browser["legacyAwardedTasks"])
        self.assertEqual(self.compile("m01-p06").json["progress"]["state"]["xp"], 70)

    def test_two_browser_copies_merge_new_task_events_only_once(self):
        first = {"version": 1, "xp": 10, "legacyXp": 0, "legacyAwardedTasks": [],
                 "awardedTasks": ["m01-l01-q1"], "xpLedger": {"task:m01-l01-q1": 10}}
        second = {"version": 1, "xp": 30, "legacyXp": 0, "legacyAwardedTasks": [],
                  "awardedTasks": ["m01-l01-q1", "m01-l01-q2"],
                  "xpLedger": {"task:m01-l01-q1": 10, "task:m01-l01-q2": 20}}
        for snapshot, expected in [(first, 10), (second, 30), (first, 30), (second, 30)]:
            merged = self.client.post("/api/progress/merge", json={
                "source": "browser", "state": snapshot}).json["state"]
            self.assertEqual(merged["xp"], expected)
        self.assertCountEqual(merged["awardedTasks"], ["m01-l01-q1", "m01-l01-q2"])

    def test_hardcore_awards_more_xp_and_unique_achievement_only_after_success(self):
        task_id = "m03-l02-hardcore"
        failed = self.compile(task_id, correct=False).json
        self.assertEqual(failed["kind"], "wrong_output")
        self.assertNotIn("progress", failed)
        self.assertEqual(self.client.get("/api/progress").json["state"]["xp"], 0)
        first = self.compile(task_id).json["progress"]["state"]
        self.assertEqual(first["xp"], 90)
        self.assertEqual(first["achievements"], ["mastery:m03-l02"])
        self.assertNotIn("m03-l02", first["completedLessons"], "optional code cannot skip required levels")
        second = self.compile(task_id).json["progress"]["state"]
        self.assertEqual(second["xp"], 90)
        self.assertEqual(second["achievements"], first["achievements"])

    def test_lab_completion_has_no_xp_and_never_exposes_hidden_cases(self):
        catalog = self.client.get("/api/ide/tasks").json
        self.assertEqual(len(catalog["tasks"]), 148)
        self.assertEqual((catalog["existingPracticeCount"], catalog["lessonHardcoreCount"]), (44, 99))
        self.assertEqual(catalog["version"], 9)
        self.assertNotIn("testCases", str(catalog))
        self.assertEqual(len({item["id"] for item in catalog["tasks"]}), 148)
        # Completing a lab cannot accidentally trigger a streak milestone:
        # in the browser labs carry no XP or daily streak reward either.
        yesterday = (datetime.now().astimezone().date() - timedelta(days=1)).isoformat()
        self.client.post("/api/progress/merge", json={"source": "browser", "state": {
            "lastActiveDate": yesterday, "streak": 6, "xp": 0}})
        lab_id = "lab-array-report"
        cases = server.code_tasks()[lab_id]["testCases"]
        outputs = [server.CommandResult(0, case["expectedOutput"], "") for case in cases]
        with patch("server.shutil.which", side_effect=lambda tool: "/fake/" + tool), \
             patch("server.run_command", side_effect=[server.CommandResult(0, "", ""), *outputs]):
            response = self.client.post("/api/compile", json={
                "taskId": lab_id, "taskType": "run", "recordProgress": True,
                "code": "public class Main { public static void main(String[] args) {} }"})
        self.assertTrue(response.json["success"])
        state = response.json["progress"]["state"]
        self.assertIn(lab_id, state["completedLabs"])
        self.assertEqual(state["xp"], 0)
        self.assertEqual(state["streak"], 6)
        self.assertNotIn("task:" + lab_id, state["xpLedger"])

    def test_streak_reconnect_and_ide_award_match_browser_rules(self):
        today = datetime.now().astimezone().date()
        yesterday = (today - timedelta(days=1)).isoformat()
        old = (today - timedelta(days=4)).isoformat()
        self.client.post("/api/progress/merge", json={"source": "browser", "state": {
            "lastActiveDate": yesterday, "streak": 6, "milestones": [], "xp": 0}})
        awarded = self.compile("m01-l02-hardcore").json["progress"]["state"]
        self.assertEqual(awarded["streak"], 7)
        self.assertEqual(awarded["lastActiveDate"], today.isoformat())
        self.assertEqual(awarded["xp"], 99 + 50)
        self.assertEqual(awarded["milestones"], [7])
        self.assertEqual(self.compile("m01-l02-hardcore").json["progress"]["state"]["xp"], 149)
        # An older tab cannot roll the active streak back to day 6.
        merged = self.client.post("/api/progress/merge", json={"source": "browser", "state": {
            "lastActiveDate": yesterday, "streak": 6, "milestones": []}}).json["state"]
        self.assertEqual((merged["streak"], merged["xp"]), (7, 149))
        # Conversely, an expired streak may not be restored just by merging
        # a stale browser that once held a larger counter and milestone.
        expired = shared_progress.merge_states({"lastActiveDate": old, "streak": 15,
                                                 "milestones": [7]},
                                                {"lastActiveDate": old, "streak": 0,
                                                 "milestones": []})
        self.assertEqual(expired["streak"], 0)
        self.assertEqual(expired["milestones"], [])

    def test_newer_drafts_win_and_cleared_drafts_do_not_resurrect(self):
        self.client.post("/api/progress/merge", json={"source": "browser", "state": {},
            "drafts": {"m02-p07": {"code": "old", "updatedAt": 5}}})
        saved = self.client.post("/api/progress/draft", json={"taskId": "m02-p07", "code": "IDE code"}).json
        self.assertEqual(saved["drafts"]["m02-p07"]["code"], "IDE code")
        old_merge = self.client.post("/api/progress/merge", json={"source": "browser", "state": {},
            "drafts": {"m02-p07": {"code": "old", "updatedAt": 5}}}).json
        self.assertEqual(old_merge["drafts"]["m02-p07"]["code"], "IDE code")
        cleared = self.client.post("/api/progress/draft", json={"taskId": "m02-p07", "code": ""}).json
        self.assertEqual(cleared["drafts"]["m02-p07"]["code"], "")
        self.assertEqual(self.client.post("/api/progress/merge", json={"state": {},
            "drafts": {"m02-p07": {"code": "old", "updatedAt": 5}}}).json["drafts"]["m02-p07"]["code"], "")
        self.assertEqual(self.client.post("/api/progress/draft", json={
            "taskId": "../private", "code": "x"}).status_code, 400)

    def test_reset_removes_only_local_profile_and_preview_keeps_it_private(self):
        self.client.post("/api/progress/merge", json={"source": "browser", "state": self.old_browser()})
        path = shared_progress.profile_path()
        self.assertTrue(path.is_file())
        self.assertEqual(self.client.post("/api/progress/reset", json={}).status_code, 400)
        with patch.dict(os.environ, {"TRAINER_HOST": "0.0.0.0"}):
            self.assertEqual(self.client.get("/api/progress").status_code, 503)
            self.assertEqual(self.client.get("/api/ide/tasks").status_code, 503)
            self.assertEqual(self.client.post("/api/progress/reset", json={"confirm": True}).status_code, 503)
        self.assertEqual(self.client.get("/api/progress").json["state"]["xp"], 70)
        self.assertEqual(self.client.get("/api/progress", headers={"Origin": "http://localhost:5173"}).status_code, 403)
        self.assertEqual(self.client.post("/api/progress/reset", json={"confirm": True},
                                          headers={"Origin": "http://localhost:5173"}).status_code, 403)
        reset = self.client.post("/api/progress/reset", json={"confirm": True}).json
        self.assertEqual(reset["state"]["xp"], 0)
        self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
