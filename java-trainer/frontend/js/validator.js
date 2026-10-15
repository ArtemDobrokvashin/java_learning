// Non-code answers are checked in the browser. Code is ALWAYS judged by javac
// on the server; client validation only catches empty/oversized submissions.
// Copy tasks reproduce an authored *example*; they are not a substitute for
// writing and compiling the independent hardcore program.
export function copyTokens(text, language = 'java') {
  const value = String(text || '').replace(/\r\n?/g, '\n').trim();
  if (language !== 'java') {
    // YAML indentation is semantic, so non-Java examples keep their layout.
    return value.split('\n').map((line) => line.trimEnd()).join('\n');
  }
  // Java formatting is flexible. Compare the significant token sequence,
  // preserving whitespace *inside* strings and comments. A changed name,
  // literal, bracket or operator is not accepted as a copy of the sample.
  return value.match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\/|[\p{L}_$][\p{L}\p{N}_$]*|\d+(?:[._]\d+)*|[^\s]/gu) || [];
}

function matchesFillBlank(blank, input) {
  // Some prompts ask for a valid value, not the particular number used in the
  // theory example. Only opt-in fields use a range; other blanks stay exact.
  if (blank.validation?.kind === 'integerRange') {
    if (!/^-?(?:0|[1-9][0-9]*)$/.test(input)) return false;
    const number = Number(input);
    return Number.isSafeInteger(number)
      && number >= blank.validation.min && number <= blank.validation.max;
  }
  const accepted = blank.accepted || [blank.answer];
  return accepted.some((answer) => input === String(answer).trim());
}

export function validateInput(task, value) {
  if (task.type === 'code' || task.type === 'copy') {
    if (!String(value || '').trim()) return task.type === 'copy'
      ? 'Перепишите пример в поле справа от образца.' : 'Напишите несколько строк Java-кода перед проверкой.';
    if (new TextEncoder().encode(value).length > 16_384) return 'Ответ слишком длинный: максимум 16 КБ.';
  } else if (task.type === 'choice' && (!Array.isArray(value) || value.length === 0)) {
    return 'Сначала выберите вариант ответа.';
  } else if (task.type === 'fill' && task.blanks.some((blank) => !String(value?.[blank.id] ?? '').trim())) {
    return 'Заполните все пропуски.';
  } else if (task.type === 'matching' && task.pairs.some((pair) => !value?.[pair.id])) {
    return 'Соедините все пары: сначала нажмите слева, потом справа.';
  }
  return null;
}

// Return null for an invalid order, or the indentation issues for the best
// valid order. Authored alternativeOrders allow ONLY documented reorderings
// (for example, independent Builder setters or YAML jobs). Compare visible
// text, not IDs, so two identical closing braces remain interchangeable.
export function parsonsIndentIssues(task, value) {
  if (!Array.isArray(value) || value.length !== task.lines.length) return null;
  const textById = new Map(task.lines.map((line) => [line.id, line.text]));
  if (new Set(value.map((line) => line?.id)).size !== task.lines.length
      || value.some((line) => !textById.has(line?.id))) return null;

  const indentById = new Map(task.solution.map(({ id, indent }) => [id, indent]));
  const orders = [task.solution.map(({ id }) => id), ...(task.alternativeOrders || [])];
  let best = null;
  for (const order of orders) {
    if (!order.every((id, index) => textById.get(value[index].id) === textById.get(id))) continue;
    const issues = order.flatMap((id, index) => value[index].indent === indentById.get(id) ? [] : [{
      id: value[index].id, row: index + 1, current: value[index].indent, expected: indentById.get(id),
    }]);
    if (!best || issues.length < best.length) best = issues;
    if (best.length === 0) break;
  }
  return best;
}

export function isCorrect(task, value) {
  switch (task.type) {
    case 'copy': {
      const actual = copyTokens(value, task.language);
      const expected = copyTokens(task.sourceCode, task.language);
      return Array.isArray(actual) ? JSON.stringify(actual) === JSON.stringify(expected) : actual === expected;
    }
    case 'choice':
      return value.length === task.correct.length && task.correct.every((id) => value.includes(id));
    case 'fill':
      return task.blanks.every((blank) =>
        matchesFillBlank(blank, String(value?.[blank.id] ?? '').trim()));
    case 'matching':
      return task.pairs.every((pair) => value[pair.id] === pair.id);
    case 'parsons':
      return parsonsIndentIssues(task, value)?.length === 0;
    default:
      throw new Error(`Неизвестный тип задания: ${task.type}`);
  }
}

export function codeHint(error, kind) {
  if (kind === 'timeout') return 'Возможно, цикл не завершается или программа ждёт ввод из консоли.';
  if (kind === 'wrong_output') return 'Проверьте порядок строк, пробелы и вычисления в System.out.println(...).';
  if (/['‘];['’] expected|; expected/i.test(error)) return 'Похоже, пропущена точка с запятой в конце команды.';
  if (/cannot find symbol/i.test(error)) return 'Проверьте имя переменной, её объявление и регистр букв.';
  if (/main method not found|Main method not found/i.test(error)) return 'Метод входа должен быть public static void main(String[] args).';
  if (/class Main is public|class .* is public, should be declared/i.test(error)) return 'Публичный класс для этих заданий должен называться Main.';
  if (/incompatible types/i.test(error)) return 'Сравните тип переменной и тип значения справа от знака =.';
  return null;
}
