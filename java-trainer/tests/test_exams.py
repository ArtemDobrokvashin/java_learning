"""Exams: persisted lifecycle, exact grading, shared judge and UTF-8 regressions."""
import io
import json
import math
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'tools')]
import exams
import server
import shared_progress
from module_exams import build_exam, project_task, task_count


def module(number):
    return json.loads((server.DATA / f'module-{number:02d}.json').read_text(encoding='utf-8'))


def answer_for(task):
    if task['type'] == 'choice':
        return task['correct']
    if task['type'] == 'fill':
        return {blank['id']: blank['answer'] for blank in task['blanks']}
    if task['type'] == 'matching':
        return {pair['id']: pair['id'] for pair in task['pairs']}
    if task['type'] == 'parsons':
        return task['solution']
    return 'public class Main { public static void main(String[] args) {} }'


class ExamTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        env = patch.dict(os.environ, {'JAVADA_PROFILE_DIR': folder.name,
                                     'TRAINER_HOST': '127.0.0.1', 'TRAINER_DISABLE_JAVA': '0'})
        env.start()
        self.addCleanup(env.stop)
        self.client = server.create_app().test_client()

    def unlock(self, number):
        shared_progress.merge_profile({'completedPractices': [number]})

    def start(self, number=1):
        return self.client.post(f'/api/progress/exams/{number}', json={'action': 'start'})

    def submit(self, attempt, answers, number=1):
        return self.client.post(f'/api/progress/exams/{number}', json={
            'action': 'submit', 'attemptId': attempt, 'answers': answers,
            'passingPercent': 1, 'xpBonus': 100000, 'retryAt': 0})

    def test_all_modules_have_reproducible_ordered_exams(self):
        ids = set()
        weights = {'easy': 1, 'medium': 2, 'hard': 3, 'complex': 4}
        for number in range(1, 23):
            content = module(number)
            exam = content['exam']
            self.assertEqual(build_exam(number, content), exam)
            self.assertEqual(len(exam['tasks']), task_count(number))
            self.assertEqual(exam['passingPercent'], 80)
            levels = [weights[task['difficulty']] for task in exam['tasks']]
            self.assertEqual(levels, sorted(levels))
            self.assertEqual([task['type'] for task in exam['tasks'][:4]], ['choice', 'fill', 'matching', 'parsons'])
            for position, task in enumerate(exam['tasks'], 1):
                self.assertNotIn(task['id'], ids)
                ids.add(task['id'])
                if task['type'] == 'code':
                    self.assertEqual(server.code_tasks()[task['id']], task)
                    reference = project_task(number, position)[1]
                    self.assertIn('class Main', reference)
                    self.assertNotIn('@N@', reference)
                    if position <= 6:
                        self.assertTrue(30 <= len(reference.splitlines()) <= 50, task['id'])
                else:
                    self.assertTrue(exams.check_answer(task, answer_for(task)), task['id'])
                    for malformed in [None, '', 42, {}, [], [None], [{'id': [], 'indent': 0}]]:
                        self.assertFalse(exams.check_answer(task, malformed), (task['id'], malformed))
        self.assertEqual(len(ids), 146)

    def test_start_is_gated_by_practice_and_survives_restart(self):
        self.assertEqual(self.start().status_code, 403)
        self.unlock(1)
        first = self.start().json
        self.assertTrue(first['active'])
        self.assertEqual(self.start().json['attemptId'], first['attemptId'])
        other = server.create_app().test_client().get('/api/progress/exams/1').json
        self.assertEqual(other['attemptId'], first['attemptId'])
        self.assertEqual(self.client.get('/api/progress/exams/99').status_code, 404)
        self.assertEqual(self.client.post('/api/progress/exams/1', json=[]).status_code, 400)
        self.assertEqual(self.submit('fake', {}).status_code, 409)

    def test_exact_80_percent_awards_once_and_unlocks_next_module(self):
        self.unlock(1)
        attempt = self.start().json['attemptId']
        # Four correct non-code answers, one skipped program => exactly 80%.
        answers = {task['id']: answer_for(task) for task in module(1)['exam']['tasks'][:4]}
        response = self.submit(attempt, answers)
        self.assertEqual(response.status_code, 200, response.json)
        self.assertTrue(response.json['result']['passed'])
        self.assertEqual(response.json['result']['percent'], 80)
        state = response.json['progress']['state']
        self.assertEqual(state['xpLedger'], {'exam:1': 310})
        self.assertEqual(state['xp'], 310)
        self.assertEqual(state['achievements'], ['exam:m01'])
        self.assertEqual(state['examScores']['1'], {'best': 80, 'passed': True})
        self.assertEqual(state['attempts'], {})
        self.assertEqual(state['awardedTasks'], [])
        again = self.submit(attempt, {})
        self.assertEqual(again.json['result'], response.json['result'])
        self.assertEqual(again.json['progress']['state']['xp'], 310)
        states = self.client.get('/api/modules?completed=1&examPassed=1').json['modules']
        self.assertEqual([entry['status'] for entry in states[:3]], ['completed', 'available', 'locked'])
        # An old browser/IDE merge cannot erase the server's pass or cooldown.
        merged = shared_progress.merge_profile({'completedLessons': ['m01-l01'], 'examScores': {}})
        self.assertEqual(merged['state']['examScores'], state['examScores'])
        self.assertEqual(merged['exams']['1']['result'], response.json['result'])
        self.assertTrue(shared_progress.profile_path().is_file())

    def test_failed_attempt_enforces_server_clock_cooldown_across_reload(self):
        self.unlock(1)
        with patch('exams.time.time', return_value=1000):
            attempt = self.start().json['attemptId']
            result = self.submit(attempt, {}).json
        self.assertFalse(result['result']['passed'])
        self.assertEqual(result['retryAt'], 4600)
        self.assertEqual(result['progress']['state']['xp'], 0)
        with patch('exams.time.time', return_value=4599):
            self.assertEqual(self.start().json['attemptId'], attempt)
            self.assertFalse(self.start().json['active'])
        with patch('exams.time.time', return_value=4600):
            new = self.start().json
        self.assertNotEqual(new['attemptId'], attempt)
        self.assertTrue(new['active'])
        self.assertEqual(self.submit(attempt, {}).status_code, 409)

    def test_no_rounding_up_for_six_seven_eight_tasks(self):
        for number in [6, 11, 16]:
            self.unlock(number)
            attempt = self.start(number).json['attemptId']
            content = module(number)
            total = len(content['exam']['tasks'])
            threshold = math.ceil(total * .8)
            # Below threshold, including correct programs checked on the server.
            answers = {t['id']: answer_for(t) for t in content['exam']['tasks'][:threshold - 1]}
            with patch('server.judge_java', return_value=server.app.json.response(success=True, kind='ok', error='')):
                response = self.submit(attempt, answers, number)
            self.assertEqual(response.status_code, 200, response.json)
            self.assertFalse(response.json['result']['passed'])
            self.assertLess(response.json['result']['percent'], 80)

    def test_minimum_passing_scores_for_all_exam_sizes(self):
        for number in [6, 11, 16]:
            self.unlock(number)
            attempt = self.start(number).json['attemptId']
            tasks = module(number)['exam']['tasks']
            threshold = math.ceil(len(tasks) * .8)
            answers = {task['id']: answer_for(task) for task in tasks[:threshold]}
            with patch('server.judge_java', return_value=server.app.json.response(
                    success=True, kind='ok', error='')):
                response = self.submit(attempt, answers, number)
            self.assertEqual(response.status_code, 200, response.json)
            self.assertTrue(response.json['result']['passed'])
            self.assertEqual(response.json['result']['correct'], threshold)
            self.assertEqual(response.json['progress']['state']['xpLedger'][f'exam:{number}'], 300 + number * 10)

    def test_reset_during_java_check_cannot_resurrect_progress(self):
        self.unlock(1)
        attempt = self.start().json['attemptId']
        task = module(1)['exam']['tasks'][-1]

        def interrupted(*args, **kwargs):
            shared_progress.reset_profile()
            return server.app.json.response(success=True, kind='ok', error='')

        with patch('server.judge_java', side_effect=interrupted):
            response = self.submit(attempt, {task['id']: answer_for(task)})
        self.assertEqual(response.status_code, 409)
        self.assertEqual(shared_progress.get_profile()['exams'], {})
        self.assertEqual(shared_progress.get_profile()['state']['xp'], 0)

    def test_overlapping_submissions_receive_retryable_busy_response(self):
        self.unlock(1)
        attempt = self.start().json['attemptId']
        with exams.LOCK:
            self.assertEqual(self.submit(attempt, {}).status_code, 429)
        self.assertTrue(self.client.get('/api/progress/exams/1').json['active'])

    @patch('server.shutil.which', side_effect=lambda name: '/fake/' + name)
    @patch('server.run_command')
    def test_exam_code_uses_utf8_judge_and_does_not_award_task_xp(self, command, _which):
        self.unlock(1)
        attempt = self.start().json['attemptId']
        task = module(1)['exam']['tasks'][-1]
        command.side_effect = [server.CommandResult(0, '', ''), server.CommandResult(0, task['expectedOutput'], '')]
        answers = {t['id']: answer_for(t) for t in module(1)['exam']['tasks']}
        result = self.submit(attempt, answers).json
        self.assertEqual(result['result']['percent'], 100)
        self.assertEqual(result['progress']['state']['xp'], 310)
        javac = command.call_args_list[0].args[0]
        java = command.call_args_list[1].args[0]
        self.assertIn('-encoding', javac)
        self.assertEqual(javac[javac.index('-encoding') + 1], 'UTF-8')
        for flag in server.JVM_UTF8_OPTIONS:
            self.assertIn(flag, java)
            self.assertIn('-J' + flag, javac)
        self.assertEqual(list(server.TEMP.glob('java-*')), [])

    def test_technical_failure_keeps_attempt_open_and_inputs_are_validated(self):
        self.unlock(1)
        attempt = self.start().json['attemptId']
        task = module(1)['exam']['tasks'][-1]
        for invalid in [42, ['x'], 'я' * 9000]:
            self.assertEqual(self.submit(attempt, {task['id']: invalid}).status_code, 400)
        self.assertEqual(self.submit(attempt, []).status_code, 400)
        with patch('server.judge_java', return_value=(server.app.json.response(
                success=False, kind='unavailable', error='Нет JDK'), 503)):
            failed = self.submit(attempt, {task['id']: answer_for(task)})
        self.assertEqual(failed.status_code, 503)
        record = self.client.get('/api/progress/exams/1').json
        self.assertTrue(record['active'])
        self.assertEqual(record['retryAt'], 0)
        # The shared judge lock must not leak on a failed submission.
        self.assertTrue(exams.LOCK.acquire(blocking=False))
        exams.LOCK.release()

    def test_public_preview_and_cross_origin_cannot_read_or_mutate_exam(self):
        with patch.dict(os.environ, {'TRAINER_HOST': '0.0.0.0'}):
            self.assertEqual(self.start().status_code, 503)
            self.assertEqual(self.client.get('/api/progress/exams/1').status_code, 503)
        response = self.client.post('/api/progress/exams/1', json={'action': 'start'},
                                    headers={'Origin': 'http://localhost:5173'})
        self.assertEqual(response.status_code, 403)

    def test_browser_cannot_certify_an_exam_via_normal_progress_merge(self):
        response = shared_progress.merge_profile({'examScores': {'1': {'passed': True, 'best': 100}}})
        self.assertEqual(response['state']['examScores'], {})


class EncodingTests(unittest.TestCase):
    def test_both_subprocess_streams_decode_utf8_and_replace_invalid_bytes(self):
        program = "import sys; sys.stdout.buffer.write('Мяч'.encode('utf-8')); sys.stderr.buffer.write('Ошибка: Мяч'.encode('utf-8')+b'\\xff')"
        with tempfile.TemporaryDirectory(dir=server.TEMP) as folder:
            result = server.run_command([sys.executable, '-c', program], Path(folder), 3, compile_step=False)
        self.assertEqual(result.stdout, 'Мяч')
        self.assertEqual(result.stderr, 'Ошибка: Мяч\ufffd')
        self.assertEqual(server._read_capped(io.BytesIO('Привет'.encode('utf-8'))), 'Привет')

    @patch('server.shutil.which', side_effect=lambda name: '/fake/' + name)
    def test_source_is_written_as_utf8_before_compilation(self, _which):
        code = 'public class Main { String item = "Мяч"; }'
        def compile_source(args, directory, timeout, **kwargs):
            self.assertEqual((directory / 'Main.java').read_bytes(), code.encode('utf-8'))
            return server.CommandResult(0, '', '')
        with patch('server.run_command', side_effect=compile_source):
            response = server.create_app().test_client().post('/api/compile', json={'code': code, 'taskType': 'compile'})
        self.assertTrue(response.json['success'])

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK not installed')
    def test_real_cyrillic_java_and_compiler_diagnostics(self):
        client = server.create_app().test_client()
        code = 'public class Main { public static void main(String[] args) { String item = "Мяч"; int units = 7; boolean inStock = true; System.out.println(item); System.out.println(units); System.out.println(inStock); } }'
        response = client.post('/api/compile', json={'code': code, 'taskType': 'run', 'taskId': 'm01-l01-hardcore'})
        self.assertTrue(response.json['success'], response.json)
        self.assertEqual(response.json['output'], 'Мяч\n7\ntrue\n')
        broken = code.replace('System.out.println(item);', 'System.out.println(Мяч);')
        response = client.post('/api/compile', json={'code': broken, 'taskType': 'compile'})
        self.assertEqual(response.json['kind'], 'compile_error')
        self.assertIn('Мяч', response.json['error'])
        self.assertNotIn('\ufffd', response.json['error'])

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK not installed')
    def test_all_58_exam_reference_programs_compile_and_run(self):
        app = server.create_app()
        with app.app_context():
            for number in range(1, 23):
                for position in range(5, task_count(number) + 1):
                    task, source = project_task(number, position)
                    with self.subTest(module=number, task=position):
                        response = app.make_response(server.judge_java(source, 'run', f'm-exam-{number:02d}-{position:02d}', task))
                        self.assertEqual(response.status_code, 200, response.json)
                        self.assertTrue(response.json['success'], response.json)


if __name__ == '__main__':
    unittest.main()
