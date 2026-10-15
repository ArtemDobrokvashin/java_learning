"""Small authoring helpers for the complete, editable JSON course (modules 4–22).

The browser and Flask server consume the generated JSON, never this Python
module. Keeping exercise definitions in Python makes IDs and schema checks
reproducible without adding a runtime dependency or a build step for learners.
"""

from __future__ import annotations

import json
from pathlib import Path

from lesson_notes import note_for
from practice_guides import primer_for
from mastery_challenges import lesson_tasks

DATA = Path(__file__).resolve().parents[1] / "frontend" / "data"


def Q(prompt, options, correct, hint, explanation, *, multiple=False, theory=False):
    """A single- or multiple-choice question with four authored alternatives."""
    assert len(options) == 4 and len(set(options)) == 4, prompt
    letters = list(correct) if isinstance(correct, str) else list(correct)
    assert letters and all(letter in 'abcd' for letter in letters), prompt
    assert len(set(letters)) == len(letters) and (multiple or len(letters) == 1), prompt
    task = dict(type='choice', prompt=prompt,
                options=[dict(id=letter, label=label) for letter, label in zip('abcd', options)],
                correct=letters, multiple=multiple, hint=hint, explanation=explanation)
    if theory:
        task['variant'] = 'theory'
    return task


def F(prompt, template, answer, options, hint, explanation, *, kind='select',
      accepted=None, validation=None):
    """One cloze field. Author equivalent literals or a valid input range explicitly."""
    assert len(template) == 2, prompt
    assert kind in ('select', 'text') and not (accepted is not None and validation is not None), prompt
    blank = dict(id='answer', kind=kind, answer=str(answer))
    if kind == 'select':
        assert str(answer) in options and len(options) >= 2, prompt
        blank['options'] = options
    if accepted is not None:
        answers = [str(value) for value in accepted]
        assert str(answer) in answers and len(set(answers)) == len(answers), prompt
        assert kind != 'select' or set(answers).issubset(options), prompt
        blank['accepted'] = answers
    if validation is not None:
        assert kind == 'text' and validation.get('kind') == 'integerRange', prompt
        assert isinstance(validation.get('min'), int) and isinstance(validation.get('max'), int), prompt
        assert validation['min'] <= validation['max'], prompt
        blank['validation'] = validation
    return dict(type='fill', prompt=prompt, template=template, blanks=[blank],
                hint=hint, explanation=explanation)


def M(prompt, pairs, hint, explanation):
    assert len(pairs) in (3, 4, 5), prompt
    assert len({left for left, _ in pairs}) == len(pairs), prompt
    assert len({right for _, right in pairs}) == len(pairs), prompt
    return dict(type='matching', prompt=prompt,
                pairs=[dict(id=str(index), left=left, right=right)
                       for index, (left, right) in enumerate(pairs, 1)],
                hint=hint, explanation=explanation)


def P(prompt, sequence, hint, explanation, *, alternative_orders=()):
    """Author valid line orders; indentation always travels with each line.

    Only explicitly authored permutations are accepted. This avoids accepting
    unsafe reorderings of dependent statements while allowing equivalent Java
    class member orders and independent YAML mapping entries.
    """
    assert 3 <= len(sequence) <= 12, prompt
    assert all(0 <= indent <= 3 for _, indent in sequence), prompt
    lines = [dict(id=chr(97 + index), text=text) for index, (text, _) in enumerate(sequence)]
    solution = [dict(id=line['id'], indent=indent)
                for line, (_, indent) in zip(lines, sequence)]
    task = dict(type='parsons', prompt=prompt, lines=lines, solution=solution,
                hint=hint, explanation=explanation)
    if alternative_orders:
        canonical = tuple(line['id'] for line in solution)
        orders = [tuple(order) for order in alternative_orders]
        assert len(orders) == len(set(orders)), prompt
        assert all(len(order) == len(canonical) and set(order) == set(canonical)
                   and order != canonical for order in orders), prompt
        task['alternativeOrders'] = [list(order) for order in orders]
    return task


DEFAULT_STARTER = (
    'public class Main {\n'
    '    public static void main(String[] args) {\n'
    '        // Напишите решение и выведите результат\n'
    '    }\n'
    '}\n'
)


def C(prompt, expected_output, hint, explanation, *, starter=DEFAULT_STARTER):
    assert 'class Main' in starter and expected_output, prompt
    return dict(type='code', prompt=prompt, starterCode=starter,
                mode='run', expectedOutput=expected_output,
                hint=hint, explanation=explanation)


def L(paragraph_one, paragraph_two, code, quiz, cloze,
      *, caption='', language='java', duration=7):
    assert len(paragraph_one) >= 75 and len(paragraph_two) >= 75, paragraph_one
    assert code and quiz['type'] == 'choice' and cloze['type'] == 'fill'
    return dict(duration=duration,
                theory=dict(paragraphs=[paragraph_one, paragraph_two],
                            code=code, caption=caption, language=language),
                tasks=[quiz, cloze])


def practice(title, description, quiz, cloze, matching, parsons, multi, code_one, code_two):
    assert multi['type'] == 'choice' and multi['multiple'], title
    return dict(title=title, description=description,
                tasks=[quiz, cloze, matching, parsons, multi, code_one, code_two])


def build_module(number, spec, index):
    entry = index['modules'][number - 1]
    assert entry['id'] == number and len(spec['lessons']) == len(entry['lessons'])
    prefix = f'm{number:02d}'
    lessons = []
    for n, (lesson, metadata) in enumerate(zip(spec['lessons'], entry['lessons']), 1):
        theory = {**lesson['theory'], **note_for(number, n)}
        original = [{**task, 'id': f'{prefix}-l{n:02d}-q{i}'}
                    for i, task in enumerate(lesson['tasks'], 1)]
        lessons.append({
            'id': metadata['id'], 'title': metadata['title'], 'duration': lesson['duration'],
            'theory': theory,
            'tasks': lesson_tasks(number, n, metadata['id'], original, theory, starter=DEFAULT_STARTER),
        })
    result = dict(id=number, title=entry['title'], lessons=lessons,
                  practice={**spec['practice'], 'id': entry['practiceId'],
                            'primer': primer_for(number),
                            'tasks': [{**task, 'id': f'{prefix}-p{i:02d}'}
                                      for i, task in enumerate(spec['practice']['tasks'], 1)]})
    assert len(result['practice']['tasks']) == 7
    assert {task['type'] for task in result['practice']['tasks']} == {
        'choice', 'fill', 'matching', 'parsons', 'code'}
    if entry.get('boss'):
        assert len(spec.get('boss', [])) == entry['boss']['questionCount'] == 5
        result['boss'] = dict(id=entry['boss']['id'], title='Стоп-урок',
                              description='Мини-экзамен: четыре верных ответа из пяти для зачёта.',
                              passingScore=entry['boss']['passingScore'],
                              tasks=[{**task, 'id': f'{prefix}-b{i:02d}'}
                                     for i, task in enumerate(spec['boss'], 1)])
    else:
        assert 'boss' not in spec
    ids = [task['id'] for part in result['lessons'] + [result['practice']] +
           ([result['boss']] if 'boss' in result else []) for task in part['tasks']]
    assert len(ids) == len(set(ids)), number
    assert all(task.get('prompt') and task.get('hint') and task.get('explanation')
               for part in result['lessons'] + [result['practice']] +
               ([result['boss']] if 'boss' in result else []) for task in part['tasks'])
    from module_exams import build_exam
    result['exam'] = build_exam(number, result)
    return result


def save_group(specs):
    index = json.loads((DATA / 'modules.json').read_text(encoding='utf-8'))
    for number, spec in sorted(specs.items()):
        result = build_module(number, spec, index)
        path = DATA / f'module-{number:02d}.json'
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        count = sum(len(lesson['tasks']) for lesson in result['lessons']) + 7 + (5 if 'boss' in result else 0)
        print(f'{path.name}: {len(result["lessons"])} уроков, {count} заданий')
