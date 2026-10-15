"""Reproducible exam authoring: 5 / 6 / 7 / 8 increasingly difficult tasks.

First four tasks deliberately retrieve key concepts already encountered in the
module (no new theory in a summative assessment). Project tasks are independent
of practice and combine the module's outcomes. Reference solutions stay in tools.
"""
from copy import deepcopy
from exam_projects import project

STARTER = '''public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: реализуйте программу по условиям задания.
    }
}
'''


def task_count(number):
    return 5 if number <= 5 else 6 if number <= 10 else 7 if number <= 15 else 8


def project_task(number, position):
    scenario = position - 4
    prompt, source, output = project(number, scenario)
    if position == 6:
        prompt = ('Расширенный сценарий. Реализуйте программу заново для изменённых данных; '
                  'проверьте граничные условия и не подменяйте вычисления печатью констант.\n\n' + prompt)
    if position >= 7:
        # Compose the domain logic with an independently testable persistence layer.
        # Capturing the reference output here is a test fixture, not a required implementation.
        source = source.replace('public class Main {', 'class Domain {')
        wrapper = '''
public class Main {
    public static void main(String[] args) throws Exception {
        java.io.PrintStream original = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.PrintStream captured = new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(captured);
            Domain.main(args);
        } finally {
            System.setOut(original);
        }
        java.nio.file.Path path = java.nio.file.Path.of("exam-report.txt");
        java.nio.file.Files.writeString(path, bytes.toString(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<String> rows = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (rows.isEmpty()) throw new IllegalStateException("Пустой отчёт");
        for (String row : rows) {
            System.out.println("Отчёт: " + row);
        }
        System.out.println("Строк: " + rows.size());
'''
        prompt = ('Мини-проект: предметная логика + коллекции + UTF-8 файл.\n\n' + prompt +
                  '\n\nНе печатайте предметный результат сразу. Соберите его строки в List<String>, '
                  'запишите в относительный exam-report.txt (UTF-8), затем прочитайте файл. '
                  'Если список пуст, бросьте IllegalStateException. Печатайте только прочитанные строки, '
                  'добавляя перед каждой «Отчёт: ». Последняя строка — «Строк: X», где X — число строк файла. '
                  'Обрабатывайте ресурсы безопасно. Внешние файлы и сервисы не нужны.')
        output = '\n'.join('Отчёт: ' + row for row in output.splitlines()) + f'\nСтрок: {len(output.splitlines())}'
        if position == 8:
            prompt += ('\nДополнительно обработайте строки {"4", "oops", "-1", "6"}: '
                       'Integer.parseInt и проверка отрицательных значений через IllegalArgumentException. '
                       'Суммируйте только неотрицательные числа, посчитайте отклонённые строки. '
                       'ДОПИШИТЕ в тот же файл строку «Аудит: сумма/ошибки». После основного вывода '
                       'прочитайте файл повторно и выведите только последнюю строку (строку аудита).')
            wrapper += '''
        int total = 0;
        int errors = 0;
        for (String value : new String[]{"4", "oops", "-1", "6"}) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0) throw new IllegalArgumentException("Отрицательное");
                total += parsed;
            } catch (IllegalArgumentException e) {
                errors++;
            }
        }
        String audit = "Аудит: " + total + "/" + errors;
        java.nio.file.Files.writeString(path, audit + "\\n", java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        java.util.List<String> updated = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println(updated.get(updated.size() - 1));
'''
            output += '\nАудит: 10/2'
        source += wrapper + '    }\n}\n'
    task = dict(type='code', mode='run', difficulty='hard' if position <= 6 else 'complex',
                prompt=prompt, starterCode=STARTER, expectedOutput=output,
                hint='Разбейте программу на небольшие части и проверьте порядок строк отчёта.',
                explanation='Проверьте вычисления, ограничения предметной области и порядок вывода. '
                            'Ожидаемый результат:\n' + output)
    return task, source


def build_exam(number, content):
    # Retrieval questions span the module; no optional hard is required.
    practice = content['practice']['tasks']
    choices = [task for lesson in content['lessons'] for task in lesson['tasks'] if task['type'] == 'choice']
    fills = [task for lesson in content['lessons'] for task in lesson['tasks'] if task['type'] == 'fill']
    tasks = [deepcopy(choices[-1] if choices else practice[0]),
             deepcopy(fills[0] if fills else practice[1]),
             deepcopy(practice[2]), deepcopy(practice[3])]
    for position, task in enumerate(tasks, 1):
        task.update(difficulty='easy' if position <= 2 else 'medium')
        for field in ('optional', 'lessonId', 'moduleId', 'level'):
            task.pop(field, None)
    tasks.extend(project_task(number, position)[0] for position in range(5, task_count(number) + 1))
    for position, task in enumerate(tasks, 1):
        task['id'] = f'm-exam-{number:02d}-{position:02d}'
    return dict(id=f'm{number:02d}-exam', title='Мини-экзамен',
                description='Проверьте, как вы применяете материал модуля самостоятельно. '
                            'Жизни не расходуются. Пропущенные задания можно открыть снова до отправки.',
                passingPercent=80, cooldownSeconds=3600, xpBonus=300 + number * 10, tasks=tasks)


def refresh_exams(data):
    import json
    index_path = data / 'modules.json'
    index = json.loads(index_path.read_text(encoding='utf-8'))
    for metadata in index['modules']:
        number = metadata['id']
        path = data / metadata['contentFile']
        content = json.loads(path.read_text(encoding='utf-8'))
        content['exam'] = build_exam(number, content)
        metadata['exam'] = {key: content['exam'][key] for key in
                            ('id', 'title', 'passingPercent', 'cooldownSeconds', 'xpBonus')}
        metadata['exam']['questionCount'] = len(content['exam']['tasks'])
        path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    index['version'] = 9
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    from pathlib import Path
    refresh_exams(Path(__file__).resolve().parents[1] / 'frontend' / 'data')
