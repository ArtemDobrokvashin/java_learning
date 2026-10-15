"""Regenerate modules 04–22; refresh theory and practice guides in 01–03."""
import json

from course_builder import DATA, DEFAULT_STARTER, save_group
from mastery_challenges import lesson_tasks
from lesson_notes import NOTES, note_for
from practice_guides import PRIMERS, primer_for
from modules_04_08 import SPECS as first
from modules_09_13 import SPECS as second
from modules_14_18 import SPECS as third
from modules_19_22 import SPECS as fourth


def refresh_foundations():
    """Keep authored exercises/IDs in 01–03; refresh teaching material only."""
    for number in range(1, 4):
        path = DATA / f'module-{number:02d}.json'
        content = json.loads(path.read_text(encoding='utf-8'))
        assert len(content['lessons']) == len(NOTES[number])
        for position, lesson in enumerate(content['lessons'], start=1):
            assert lesson['id'] == f'm{number:02d}-l{position:02d}'
            lesson['theory'].update(note_for(number, position))
            lesson['theory']['caption'] = 'Короткий фрагмент для main; ниже — полный пример с выводом.'
            # When regenerating an already extended course, retain the two
            # original questions instead of giving them new IDs or wiping data.
            original = [task for task in lesson['tasks'] if task['id'] in
                        {f'{lesson["id"]}-q1', f'{lesson["id"]}-q2'}]
            lesson['tasks'] = lesson_tasks(number, position, lesson['id'], original,
                                           lesson['theory'], starter=DEFAULT_STARTER)
        content['practice']['primer'] = primer_for(number)
        path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f'{path.name}: обновлены разборы {len(content["lessons"])} уроков и подготовка к практике')


if __name__ == '__main__':
    assert set(NOTES) == set(PRIMERS) == set(range(1, 23))
    index_path = DATA / 'modules.json'
    index = json.loads(index_path.read_text(encoding='utf-8'))
    index['version'] = 8
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    refresh_foundations()
    save_group({**first, **second, **third, **fourth})
