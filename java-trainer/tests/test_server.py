"""API and judge tests. No JDK is needed for the mocked judge cases."""

import io
import json
import shutil
import zipfile
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server  # noqa: E402


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.client = server.create_app().test_client()

    def test_static_files_and_health(self):
        page = self.client.get("/")
        self.assertEqual(page.status_code, 200)
        self.assertIn(b"Javada", page.data)
        page.close()
        content = self.client.get("/data/module-01.json")
        self.assertEqual(content.status_code, 200)
        content.close()
        self.assertEqual(self.client.get("/../server.py").status_code, 404)
        self.assertIn("jdkAvailable", self.client.get("/api/health").json)
        response = self.client.get("/data/labs.json")
        public = response.json
        self.assertEqual(len(public["labs"]), 5)
        self.assertNotIn("testCases", json.dumps(public))
        response.close()
        self.assertEqual(self.client.get("/private/lab_cases.json").status_code, 404)

    @patch.dict("os.environ", {"TRAINER_HOST": "0.0.0.0"})
    def test_remote_preview_cannot_run_java(self):
        self.assertFalse(self.client.get("/api/health").json["jdkAvailable"])
        response = self.client.post("/api/compile", json={"code": "public class Main {}", "taskType": "compile"})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json["kind"], "unavailable")

    def test_modules_unlock_in_order_and_all_have_real_content(self):
        initial = self.client.get("/api/modules").json["modules"]
        self.assertEqual(len(initial), 22)
        self.assertTrue(all(m["contentStatus"] == "ready" and m["contentFile"] for m in initial))
        self.assertEqual([m["status"] for m in initial[:4]], ["available", "locked", "locked", "locked"])
        after_one = self.client.get("/api/modules?completed=1&examPassed=1").json["modules"]
        self.assertEqual([m["status"] for m in after_one[:3]], ["completed", "available", "locked"])
        after_three = self.client.get("/api/modules?completed=1,2,3&examPassed=1,2,3").json["modules"]
        self.assertEqual(after_three[3]["status"], "available")
        self.assertEqual(after_three[4]["status"], "locked")
        for metadata in initial:
            response = self.client.get("/data/" + metadata["contentFile"])
            self.assertEqual(response.status_code, 200, metadata["id"])
            content = response.json
            self.assertEqual(content["id"], metadata["id"])
            self.assertEqual(len(content["lessons"]), len(metadata["lessons"]))
            self.assertEqual(len(content["practice"]["tasks"]), 7)
            if metadata.get("boss"):
                self.assertEqual(len(content["boss"]["tasks"]), 5)
            response.close()
        self.assertEqual(len(server.code_tasks()), 206)
        self.assertEqual(server.code_tasks()["m22-p07"]["expectedOutput"], "Остановлено: test")
        self.assertEqual(len(server.code_tasks()["lab-array-report"]["testCases"]), 4)

    def test_boss_blocks_following_module_even_after_practice(self):
        completed = set(range(1, 18))
        states = server.modules_with_status(completed, set(), completed)
        self.assertEqual(states[16]["status"], "available")
        self.assertEqual(states[17]["status"], "locked")
        states = server.modules_with_status(completed, {17}, completed)
        self.assertEqual(states[16]["status"], "completed")
        self.assertEqual(states[17]["status"], "available")

    def test_project_starters_are_complete_and_whitelisted(self):
        for name, expected in (
            ("task-tracker", "task-tracker/src/Main.java"),
            ("task-api", "task-api/pom.xml"),
        ):
            response = self.client.get(f"/api/projects/{name}/starter")
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.mimetype, "application/zip")
            with zipfile.ZipFile(io.BytesIO(response.data)) as archive:
                names = archive.namelist()
                self.assertIn(expected, names)
                self.assertIn(f"{name}/README.md", names)
                self.assertIn(f"{name}/.github/workflows/verify.yml", names)
                self.assertTrue(all(path.startswith(f"{name}/") for path in names))
                self.assertFalse(any("private/" in path or "tasks-db" in path for path in names))
        self.assertEqual(self.client.get("/api/projects/other/starter").status_code, 404)
        self.assertEqual(self.client.get("/api/projects/../starter").status_code, 404)

    def test_bad_query_and_request_payloads(self):
        self.assertEqual(self.client.get("/api/modules?completed=99").status_code, 400)
        self.assertEqual(self.client.get("/api/modules?completed=1,foo").status_code, 400)
        self.assertEqual(self.client.post("/api/compile", data="text").status_code, 400)
        self.assertEqual(self.client.post("/api/compile", json={"code": "", "taskType": "run"}).status_code, 400)
        self.assertEqual(self.client.post("/api/compile", json={"code": "x" * 17_000, "taskType": "compile"}).status_code, 400)
        self.assertEqual(self.client.post("/api/compile", json={"code": "x" * 30_000, "taskType": "compile"}).status_code, 413)
        self.assertEqual(self.client.post("/api/compile", json={"code": "class Main {}", "taskType": "run"}).status_code, 400)
        self.assertEqual(self.client.post("/api/compile", json={"code": "class Main {}", "taskType": "run", "taskId": "fake"}).status_code, 400)
        self.assertEqual(self.client.post("/api/compile", json={"code": "class Main {}", "taskType": "delete"}).status_code, 400)

    def test_only_same_or_local_origins_are_permitted(self):
        headers = {"Origin": "https://example.com"}
        response = self.client.post("/api/compile", json={"code": "class Main {}", "taskType": "compile"}, headers=headers)
        self.assertEqual(response.status_code, 403)
        response = self.client.options("/api/compile", headers={"Origin": "http://localhost:5173"})
        self.assertEqual(response.status_code, 204)
        self.assertEqual(response.headers["Access-Control-Allow-Origin"], "http://localhost:5173")

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_success_and_incorrect_output(self, _which, command):
        code = "public class Main { public static void main(String[] args) {} }"
        command.side_effect = [server.CommandResult(0, "", ""), server.CommandResult(0, "30\nAlice\nfalse\n", "")]
        response = self.client.post("/api/compile", json={"code": code, "taskType": "run", "taskId": "m01-p06"})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["success"])
        self.assertEqual(response.json["output"], "30\nAlice\nfalse\n")
        command.side_effect = [server.CommandResult(0, "", ""), server.CommandResult(0, "oops\n", "")]
        response = self.client.post("/api/compile", json={"code": code, "taskType": "run", "taskId": "m01-p06"})
        self.assertFalse(response.json["success"])
        self.assertEqual(response.json["kind"], "wrong_output")
        self.assertEqual(response.json["output"], "oops\n")
        self.assertEqual(list(server.TEMP.glob("java-*")), [])

    def test_output_padding_is_ignored_but_meaningful_spacing_is_not(self):
        self.assertEqual(server.normalized_output("0 1 2 3  \r\n"), "0 1 2 3")
        self.assertEqual(server.normalized_output("Alice  \r\nBob\t\r\n"), "Alice\nBob")
        self.assertNotEqual(server.normalized_output("Alice :31\n"), server.normalized_output("Alice:31"))
        self.assertNotEqual(server.normalized_output("Bob\nAlice"), server.normalized_output("Alice\nBob"))

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_all_44_code_task_ids_route_to_server_owned_expected_output(self, _which, command):
        cases = {id: task for id, task in server.code_tasks().items() if "testCases" not in task}
        self.assertEqual(len(cases), 201)
        self.assertEqual(len([id for id in cases if '-p' in id]), 44)
        self.assertEqual(len([id for id in cases if id.endswith('-hardcore')]), 99)
        for id, task in cases.items():
            with self.subTest(task=id):
                command.side_effect = [server.CommandResult(0, "", ""),
                                       server.CommandResult(0, task["expectedOutput"] + "  \n", "")]
                response = self.client.post("/api/compile", json={
                    "code": "public class Main { public static void main(String[] args) {} }",
                    "taskType": "run", "taskId": id,
                    "expectedOutput": "client cannot replace the official answer"})
                self.assertEqual(response.status_code, 200)
                self.assertTrue(response.json["success"], (id, response.json))
                self.assertEqual(command.call_count, 2)
                command.reset_mock()
        self.assertEqual(list(server.TEMP.glob("java-*")), [])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_new_module_code_uses_server_owned_answer(self, _which, command):
        code = 'public class Main { public static void main(String[] a) { System.out.println("OK"); } }'
        payload = {"code": code, "taskType": "run", "taskId": "m22-p07",
                   "expectedOutput": "incorrect client-supplied answer"}
        command.side_effect = [server.CommandResult(0, "", ""),
                               server.CommandResult(0, "Остановлено: test\n", "")]
        self.assertTrue(self.client.post("/api/compile", json=payload).json["success"])
        command.side_effect = [server.CommandResult(0, "", ""),
                               server.CommandResult(0, "incorrect client-supplied answer\n", "")]
        self.assertEqual(self.client.post("/api/compile", json=payload).json["kind"], "wrong_output")

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_lab_checks_multiple_inputs_owned_by_server(self, _which, command):
        code = 'public class Main { public static void main(String[] a) { System.out.println("0"); } }'
        payload = {"code": code, "taskType": "run", "taskId": "lab-array-report",
                   "testCases": [{"input": "fake", "expectedOutput": "fake"}],
                   "input": "fake", "expectedOutput": "fake"}
        outputs = ["-2 9 21\n", "-7 -7 -7\n", "0 0 0\n", "2000000000 2000000000 6000000000\n"]
        command.side_effect = [server.CommandResult(0, "", "")] + [
            server.CommandResult(0, output, "") for output in outputs]
        result = self.client.post("/api/compile", json=payload)
        self.assertEqual(result.status_code, 200)
        self.assertTrue(result.json["success"])
        self.assertEqual(result.json["output"], "Пройдено 4 из 4 проверок.")
        self.assertEqual(command.call_count, 5)  # javac once, java four times
        self.assertEqual(command.call_args_list[1].kwargs["stdin_data"], "5\n5 -2 9 9 0\n")
        self.assertEqual(command.call_args_list[4].kwargs["stdin_data"],
                         "3\n2000000000 2000000000 2000000000\n")
        command.side_effect = [server.CommandResult(0, "", ""),
                               server.CommandResult(0, outputs[0], ""),
                               server.CommandResult(0, "fake\n", "")]
        result = self.client.post("/api/compile", json=payload).json
        self.assertEqual(result["kind"], "wrong_output")
        self.assertIn("Проверка 2/4", result["error"])
        self.assertIn("-7 -7 -7", result["error"])
        self.assertEqual(command.call_count, 8)
        command.side_effect = [server.CommandResult(0, "", ""),
                               server.CommandResult(-9, "", "", timed_out=True)]
        self.assertEqual(self.client.post("/api/compile", json=payload).json["kind"], "timeout")
        self.assertEqual(list(server.TEMP.glob("java-*")), [])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_lab_executions_share_five_second_budget(self, _which, command):
        payload = {"code": "class Main { public static void main(String[] args) {} }",
                   "taskType": "run", "taskId": "lab-array-report"}
        command.side_effect = [server.CommandResult(0, "", ""),
                               server.CommandResult(0, "-2 9 21\n", "")]
        with patch.object(server, "time") as clock:
            clock.monotonic.side_effect = [100, 101, 106]
            result = self.client.post("/api/compile", json=payload).json
        self.assertEqual(result["kind"], "timeout")
        self.assertEqual(command.call_count, 2)  # No second run after the shared deadline.
        self.assertEqual(command.call_args_list[1].args[2], 4)

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_compiler_errors_timeout_and_compile_only(self, _which, command):
        payload = {"code": "public class Main {}", "taskType": "compile"}
        command.return_value = server.CommandResult(1, "", "/tmp/Main.java:2: ';' expected")
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["kind"], "compile_error")
        self.assertIn("expected", response.json["error"])
        command.return_value = server.CommandResult(-9, "", "", timed_out=True)
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json["kind"], "compile_timeout")
        command.return_value = server.CommandResult(0, "", "")
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["success"])
        self.assertIn("успешно", response.json["output"])

    @patch("server.shutil.which", return_value=None)
    def test_missing_jdk_is_service_error_not_wrong_answer(self, _which):
        response = self.client.post("/api/compile", json={"code": "public class Main {}", "taskType": "compile"})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json["kind"], "unavailable")

    def test_command_runner_captures_real_output_and_cleans_up(self):
        # Exercise the subprocess wrapper itself without requiring javac.
        with server.tempfile.TemporaryDirectory(dir=server.TEMP) as directory:
            result = server.run_command([sys.executable, "-c", "print('hello')"], Path(directory), 3, compile_step=False)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), "hello")
        self.assertEqual(result.stderr, "")
        with server.tempfile.TemporaryDirectory(dir=server.TEMP) as directory:
            result = server.run_command(
                [sys.executable, "-c", "import sys; print(sys.stdin.read().upper())"],
                Path(directory), 3, compile_step=False, stdin_data="привет")
        self.assertEqual(result.stdout.strip(), "ПРИВЕТ")
        self.assertEqual(list(server.TEMP.glob("java-*")), [])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_javac_failure_without_diagnostic_is_503(self, _which, command):
        payload = {"code": "public class Main {}", "taskType": "compile"}
        command.return_value = server.CommandResult(1, "", "Could not reserve enough space for 2097152KB object heap")
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json["kind"], "unavailable")
        self.assertIn("Сбой запуска компилятора javac", response.json["error"])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_jvm_start_failure_is_503(self, _which, command):
        payload = {"code": "public class Main { public static void main(String[] args) {} }",
                   "taskType": "run", "taskId": "m01-p06"}
        command.side_effect = [
            server.CommandResult(0, "", ""),
            server.CommandResult(1, "", "Error: Could not create the Java Virtual Machine.\nError: A fatal exception has occurred."),
        ]
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json["kind"], "unavailable")
        self.assertIn("Сбой запуска виртуальной машины JVM", response.json["error"])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_cyrillic_output_matching(self, _which, command):
        code = 'public class Main { public static void main(String[] args) { System.out.println("Надень куртку"); } }'
        payload = {"code": code, "taskType": "run", "taskId": "m02-p06"}
        command.side_effect = [
            server.CommandResult(0, "", ""),
            server.CommandResult(0, "Надень куртку\r\n", ""),
        ]
        response = self.client.post("/api/compile", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["success"])

        # And screenshot task m01-p06
        code_p06 = 'public class Main { public static void main(String[] args) {} }'
        payload_p06 = {"code": code_p06, "taskType": "run", "taskId": "m01-p06"}
        command.side_effect = [
            server.CommandResult(0, "", ""),
            server.CommandResult(0, "30\r\nAlice\r\nfalse\r\n", ""),
        ]
        res_p06 = self.client.post("/api/compile", json=payload_p06)
        self.assertEqual(res_p06.status_code, 200)
        self.assertTrue(res_p06.json["success"])

    @patch("server.run_command")
    @patch("server.shutil.which", side_effect=lambda tool: "/usr/bin/" + tool)
    def test_health_check_trial_probe(self, _which, command):
        command.side_effect = [
            server.CommandResult(0, "javac 17.0.2\n", ""),
            server.CommandResult(0, "", ""),
            server.CommandResult(0, "ok\n", ""),
        ]
        response = self.client.get("/api/health?refresh=1")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["jdkAvailable"])
        self.assertIn("готов", response.json["message"].lower())

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK not installed")
    def test_real_java_compiles_and_runs(self):
        code = 'public class Main { public static void main(String[] a) { System.out.println("20"); } }'
        response = self.client.post("/api/compile", json={"code": code, "taskType": "run", "taskId": "m01-p07"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertTrue(response.json["success"], response.json)


if __name__ == "__main__":
    unittest.main()
