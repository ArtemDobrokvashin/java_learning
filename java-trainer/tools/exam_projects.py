"""Authored executable exam projects (JDK 17 only, no external services).

@N@ is the scenario parameter, not user input. Sources are reference solutions
for regression tests; the browser receives only a skeleton and specification.
Framework modules deliberately use Java models of the taught architecture.
"""
from textwrap import dedent
import re

# (specification, reference source, expected output as a function of scenario)
PROJECTS = {
1: ('''Составьте отчёт магазина без циклов и методов. Название товара — «Мяч», цена — 40,
начальный остаток — 12, продано — N, поступило — 2. Рассчитайте остаток после продажи,
конечный остаток, выручку и наличие (конечный остаток > 0). Выведите название,
начальный остаток, проданное количество, остаток после продажи, конечный остаток,
цену, выручку и наличие. Затем рассчитайте и выведите: свободный остаток (резерв = 1),
стоимость конечного остатка, стоимость поступления, стоимость начального остатка,
баланс стоимости (начальная стоимость + поступление - выручка) и равенство
баланса стоимости со стоимостью конечного остатка — по одному значению на строку, без подписей.
Используйте переменные int, String и boolean, а не готовые ответы.''', '''
public class Main {
    public static void main(String[] args) {
        String item = "Мяч";
        int price = 40;
        int initial = 12;
        int sold = @N@;
        int received = 2;

        int afterSale = initial - sold;
        int finalStock = afterSale + received;
        int revenue = sold * price;
        boolean available = finalStock > 0;

        int reserved = 1;
        int sellable = finalStock - reserved;
        int inventoryValue = finalStock * price;
        int purchaseCost = received * price;
        int openingValue = initial * price;
        int closingValue = openingValue + purchaseCost - revenue;
        boolean balanced = closingValue == inventoryValue;

        System.out.println(item);
        System.out.println(initial);
        System.out.println(sold);
        System.out.println(afterSale);
        System.out.println(finalStock);
        System.out.println(price);
        System.out.println(revenue);
        System.out.println(available);
        System.out.println(sellable);
        System.out.println(inventoryValue);
        System.out.println(purchaseCost);
        System.out.println(openingValue);
        System.out.println(closingValue);
        System.out.println(balanced);
    }
}
''', lambda n: f'Мяч\n12\n{n}\n{12-n}\n{14-n}\n40\n{40*n}\ntrue\n{13-n}\n{(14-n)*40}\n80\n480\n{(14-n)*40}\ntrue'),
2: ('''Напишите программу расчёта доставки. Сумма заказа = N * 600, участник клуба = true.
При сумме >= 1000 доставка бесплатна, иначе стоит 150. Скидка участнику клуба — 100,
но только при сумме >= 1000. Статус заказа выбирается классическим switch:
1 → «Создан», 2 → «Собран», прочее → «В пути»; код статуса = N.
Выведите статус, стоимость доставки, скидку, итог (сумма + доставка - скидка),
а затем «Бесплатно» или «Платно» в зависимости от доставки — на отдельных строках.''', '''
public class Main {
    public static void main(String[] args) {
        int statusCode = @N@;
        int amount = statusCode * 600;
        boolean member = true;
        int delivery;
        if (amount >= 1000) {
            delivery = 0;
        } else {
            delivery = 150;
        }
        int discount = 0;
        if (member && amount >= 1000) {
            discount = 100;
        }
        String status;
        switch (statusCode) {
            case 1: status = "Создан"; break;
            case 2: status = "Собран"; break;
            default: status = "В пути";
        }
        int total = amount + delivery - discount;
        System.out.println(status);
        System.out.println(delivery);
        System.out.println(discount);
        System.out.println(total);
        if (delivery == 0) {
            System.out.println("Бесплатно");
        } else {
            System.out.println("Платно");
        }
    }
}
''', lambda n: f'{["Создан", "Собран", "В пути"][min(n,3)-1]}\n{150 if n==1 else 0}\n{0 if n==1 else 100}\n{750 if n==1 else n*600-100}\n{"Платно" if n==1 else "Бесплатно"}'),
3: ('''Обработайте массив {N, -2, 7, 0, N + 3}. Циклами найдите сумму всех чисел,
минимум, максимум, число положительных элементов и сумму только положительных.
Затем циклом while подсчитайте число нулей. Выведите шесть результатов в указанном
порядке, каждый на отдельной строке. Начальные минимум и максимум берите из массива.''', '''
public class Main {
    public static void main(String[] args) {
        int n = @N@;
        int[] values = {n, -2, 7, 0, n + 3};
        int sum = 0;
        int min = values[0];
        int max = values[0];
        int positiveCount = 0;
        int positiveSum = 0;
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            sum += value;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
            if (value > 0) {
                positiveCount++;
                positiveSum += value;
            }
        }
        int index = 0;
        int zeros = 0;
        while (index < values.length) {
            if (values[index] == 0) {
                zeros++;
            }
            index++;
        }
        System.out.println(sum);
        System.out.println(min);
        System.out.println(max);
        System.out.println(positiveCount);
        System.out.println(positiveSum);
        System.out.println(zeros);
    }
}
''', lambda n: f'{2*n+8}\n-2\n7\n3\n{2*n+10}\n1'),
4: ('''Создайте методы static int sum(int[]), static int countAtLeast(int[], int)
и static double average(int[]). Для пустого массива среднее равно 0.0.
В main обработайте {N, 4, 8}: выведите сумму, количество значений >= 4,
среднее, затем среднее пустого массива — на отдельных строках. Используйте вызовы методов.''', '''
public class Main {
    static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    static int countAtLeast(int[] values, int limit) {
        int count = 0;
        for (int value : values) {
            if (value >= limit) {
                count++;
            }
        }
        return count;
    }

    static double average(int[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        return (double) sum(values) / values.length;
    }

    public static void main(String[] args) {
        int[] data = {@N@, 4, 8};
        System.out.println(sum(data));
        System.out.println(countAtLeast(data, 4));
        System.out.println(average(data));
        System.out.println(average(new int[0]));
    }
}
''', lambda n: f'{n+12}\n2\n{(n+12)/3}\n0.0'),
5: ('''Создайте класс Ticket: название, цена и число проданных билетов — поля объекта;
конструктор принимает название и цену. Метод sell(int) увеличивает проданное количество,
revenue() возвращает выручку, label() — название. Статическое поле created считает объекты.
Создайте Ticket("Java", 100) и Ticket("Кино", 80), продайте N и 2 билета соответственно.
Выведите название первого события, его выручку, выручку второго и число созданных объектов.''', '''
class Ticket {
    private String title;
    private int price;
    private int sold;
    static int created;

    Ticket(String title, int price) {
        this.title = title;
        this.price = price;
        this.sold = 0;
        created++;
    }

    void sell(int count) {
        sold += count;
    }

    int revenue() {
        return price * sold;
    }

    String label() {
        return title;
    }
}

public class Main {
    public static void main(String[] args) {
        Ticket first = new Ticket("Java", 100);
        Ticket second = new Ticket("Кино", 80);
        first.sell(@N@);
        second.sell(2);
        System.out.println(first.label());
        System.out.println(first.revenue());
        System.out.println(second.revenue());
        System.out.println(Ticket.created);
    }
}
''', lambda n: f'Java\n{100*n}\n160\n2'),
6: ('''Создайте абстрактный класс Employee с полем base, конструктором и абстрактным pay().
Developer наследует Employee, возвращает base + 200, Designer — base + 100.
Интерфейс Named имеет default label(), возвращающий «Сотрудник»; Employee реализует Named.
В массиве Employee[] сохраните Developer(N * 100) и Designer(300).
Через ссылки Employee выведите label() и pay() каждого сотрудника (четыре строки).''', '''
interface Named {
    default String label() {
        return "Сотрудник";
    }
}
abstract class Employee implements Named {
    protected int base;
    Employee(int base) {
        this.base = base;
    }
    abstract int pay();
}
class Developer extends Employee {
    Developer(int base) {
        super(base);
    }
    @Override
    int pay() {
        return base + 200;
    }
}
class Designer extends Employee {
    Designer(int base) {
        super(base);
    }
    @Override
    int pay() {
        return base + 100;
    }
}
public class Main {
    public static void main(String[] args) {
        Employee[] staff = {new Developer(@N@ * 100), new Designer(300)};
        for (Employee employee : staff) {
            System.out.println(employee.label());
            System.out.println(employee.pay());
        }
    }
}
''', lambda n: f'Сотрудник\n{n*100+200}\nСотрудник\n400'),
7: ('''Создайте Account с private int balance и неизменяемым private final String owner.
Конструктор получает владельца и начальный баланс. deposit(int) принимает только положительные
суммы; withdraw(int) возвращает true и списывает деньги, только если сумма положительна
и не превышает баланс. Геттеры не меняют состояние. Для Account("Ира", N * 100)
выполните deposit(50), deposit(-10), withdraw(120), withdraw(1000).
Выведите владельца, результаты двух снятий и конечный баланс — четыре строки.''', '''
class Account {
    private final String owner;
    private int balance;

    Account(String owner, int balance) {
        this.owner = owner;
        this.balance = balance;
    }
    void deposit(int amount) {
        if (amount > 0) {
            balance += amount;
        }
    }
    boolean withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }
    String getOwner() {
        return owner;
    }
    int getBalance() {
        return balance;
    }
}
public class Main {
    public static void main(String[] args) {
        Account account = new Account("Ира", @N@ * 100);
        account.deposit(50);
        account.deposit(-10);
        System.out.println(account.getOwner());
        System.out.println(account.withdraw(120));
        System.out.println(account.withdraw(1000));
        System.out.println(account.getBalance());
    }
}
''', lambda n: f'Ира\ntrue\nfalse\n{n*100-70}'),
8: ('''Создайте интерфейс Metric с value(). Реализуйте Fixed(int), возвращающий сохранённое
число, и DoubleMetric(int), возвращающий удвоенное число. Создайте Box<T> с конструктором
и get(). Поместите в List<Metric> объекты Fixed(N), DoubleMetric(5), Fixed(3).
Метод total(List<? extends Metric>) складывает value(). Оберните результат в Box<Integer>.
Выведите сумму из Box и количество метрик — две строки. Без raw-типов и приведений.''', '''
import java.util.List;
interface Metric {
    int value();
}
class Fixed implements Metric {
    private final int number;
    Fixed(int number) {
        this.number = number;
    }
    public int value() {
        return number;
    }
}
class DoubleMetric extends Fixed {
    DoubleMetric(int number) {
        super(number);
    }
    public int value() {
        return 2 * super.value();
    }
}
class Box<T> {
    private final T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
public class Main {
    static int total(List<? extends Metric> metrics) {
        int sum = 0;
        for (Metric metric : metrics) {
            sum += metric.value();
        }
        return sum;
    }
    public static void main(String[] args) {
        List<Metric> metrics = List.of(new Fixed(@N@), new DoubleMetric(5), new Fixed(3));
        Box<Integer> result = new Box<>(total(metrics));
        System.out.println(result.get());
        System.out.println(metrics.size());
    }
}
''', lambda n: f'{n+13}\n3'),
9: ('''Обработайте List<String> со словами "java", "code", "java", "N" (N — число в строке).
Постройте TreeMap<String,Integer> частот и HashSet уникальных слов. Выведите каждую пару
из TreeMap как слово:частота в порядке ключей, затем размер множества. Используйте
getOrDefault, а не заранее вычисленные частоты. Отдельный класс WordReport хранит map
и set, метод add(String) добавляет слово, print() печатает отчёт.''', '''
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

class WordReport {
    private final Map<String, Integer> frequencies = new TreeMap<>();
    private final Set<String> unique = new HashSet<>();

    void add(String word) {
        int previous = frequencies.getOrDefault(word, 0);
        frequencies.put(word, previous + 1);
        unique.add(word);
    }

    int size() {
        return unique.size();
    }

    void print() {
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
        System.out.println(size());
    }
}

public class Main {
    public static void main(String[] args) {
        List<String> words = List.of("java", "code", "java", "@N@");
        WordReport report = new WordReport();
        for (String word : words) {
            report.add(word);
        }
        report.print();
    }
}
''', lambda n: f'{n}:1\ncode:1\njava:2\n3'),
10: ('''Создайте CsvReport с методом load(Path), который через BufferedReader в
try-with-resources читает CSV имя,число и возвращает сумму чисел. В main запишите UTF-8
файл exam.csv со строками «Мяч,N», «Книга,4», «Ручка,3». Прочитайте его методом load.
Выведите сумму, затем первую строку файла, прочитанную через Files.readAllLines.
Используйте относительный путь, файл создавайте сами. Никаких внешних библиотек.''', '''
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class CsvReport {
    int load(Path path) throws IOException {
        int total = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String row;
            while ((row = reader.readLine()) != null) {
                String[] parts = row.split(",");
                int value = Integer.parseInt(parts[1]);
                total += value;
            }
        }
        return total;
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("exam.csv");
        List<String> rows = List.of("Мяч,@N@", "Книга,4", "Ручка,3");
        Files.write(path, rows, StandardCharsets.UTF_8);
        CsvReport report = new CsvReport();
        int total = report.load(path);
        List<String> loaded = Files.readAllLines(path, StandardCharsets.UTF_8);
        System.out.println(total);
        System.out.println(loaded.get(0));
    }
}
''', lambda n: f'{n+7}\nМяч,{n}'),
11: ('''Создайте собственное unchecked-исключение NegativeValue и метод parse(String).
Он преобразует строку в int, а для отрицательного числа бросает NegativeValue.
Обработайте массив {"N", "oops", "-2", "4"}. В цикле отдельно перехватите
NumberFormatException и NegativeValue, подсчитайте ошибки и сумму корректных чисел.
Выведите сумму и количество ошибок. В finally увеличивайте счётчик обработанных
строк; выведите его третьей строкой.''', '''
class NegativeValue extends RuntimeException {
    NegativeValue(String message) {
        super(message);
    }
}
public class Main {
    static int parse(String text) {
        int number = Integer.parseInt(text);
        if (number < 0) {
            throw new NegativeValue("Отрицательное число");
        }
        return number;
    }
    public static void main(String[] args) {
        String[] rows = {"@N@", "oops", "-2", "4"};
        int total = 0;
        int errors = 0;
        int processed = 0;
        for (String row : rows) {
            try {
                total += parse(row);
            } catch (NumberFormatException e) {
                errors++;
            } catch (NegativeValue e) {
                errors++;
            } finally {
                processed++;
            }
        }
        System.out.println(total);
        System.out.println(errors);
        System.out.println(processed);
    }
}
''', lambda n: f'{n+4}\n2\n4'),
12: ('''Создайте Counter с синхронизированными increment() и get(). Запустите два Thread,
каждый увеличивает общий счётчик N * 100 раз. Дождитесь обоих через join().
Через ExecutorService с одним потоком отправьте Callable, возвращающий удвоенный get().
Выведите get(), затем результат Future.get(). Обязательно закройте executor в finally.
Вывод должен быть детерминированным при любом порядке выполнения потоков.''', '''
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
class Counter {
    private int value;
    synchronized void increment() {
        value++;
    }
    synchronized int get() {
        return value;
    }
}
public class Main {
    public static void main(String[] args) throws Exception {
        Counter counter = new Counter();
        Runnable work = () -> {
            for (int i = 0; i < @N@ * 100; i++) {
                counter.increment();
            }
        };
        Thread first = new Thread(work);
        Thread second = new Thread(work);
        first.start();
        second.start();
        first.join();
        second.join();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> doubled = executor.submit(() -> counter.get() * 2);
            System.out.println(counter.get());
            System.out.println(doubled.get());
        } finally {
            executor.shutdown();
        }
    }
}
''', lambda n: f'{n*200}\n{n*400}'),
13: ('''Создайте метод distances(int[][] graph, int start), выполняющий BFS с очередью
и возвращающий массив расстояний (-1 для недостижимых вершин). Граф ориентированный:
{{1,2},{3},{3},{},{}}. От вершины 0 выведите расстояния по порядку вершин, по одному
на строке. Затем итеративным методом fib(int) вычислите F(N + 5) при F(0)=0, F(1)=1
и выведите его. У каждой вершины отмечайте посещение при добавлении в очередь.''', '''
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
public class Main {
    static int[] distances(int[][] graph, int start) {
        int[] distance = new int[graph.length];
        Arrays.fill(distance, -1);
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        distance[start] = 0;
        while (!queue.isEmpty()) {
            int vertex = queue.remove();
            for (int next : graph[vertex]) {
                if (distance[next] == -1) {
                    distance[next] = distance[vertex] + 1;
                    queue.add(next);
                }
            }
        }
        return distance;
    }
    static int fib(int n) {
        int a = 0;
        int b = 1;
        for (int i = 0; i < n; i++) {
            int next = a + b;
            a = b;
            b = next;
        }
        return a;
    }
    public static void main(String[] args) {
        int[][] graph = {{1, 2}, {3}, {3}, {}, {}};
        for (int distance : distances(graph, 0)) {
            System.out.println(distance);
        }
        System.out.println(fib(@N@ + 5));
    }
}
''', lambda n: f'0\n1\n1\n2\n-1\n{[8,13,21,34][n-1]}'),
14: ('''Реализуйте неизменяемый Report с вложенным Builder: title(String), count(int), build().
Значения по умолчанию — «Отчёт» и 0. Интерфейс Formatter имеет format(Report).
Две стратегии форматируют отчёт: Plain → title:count, Brackets → [title=count].
Постройте Report с title «Java» и count N, выведите обе стратегии по порядку.
Передавайте стратегию в отдельный метод render, не используйте instanceof или switch.''', '''
interface Formatter {
    String format(Report report);
}
class Report {
    final String title;
    final int count;
    private Report(Builder builder) {
        title = builder.title;
        count = builder.count;
    }
    static class Builder {
        private String title = "Отчёт";
        private int count;
        Builder title(String value) {
            title = value;
            return this;
        }
        Builder count(int value) {
            count = value;
            return this;
        }
        Report build() { return new Report(this); }
    }
}
class Plain implements Formatter {
    public String format(Report r) { return r.title + ":" + r.count; }
}
class Brackets implements Formatter {
    public String format(Report r) { return "[" + r.title + "=" + r.count + "]"; }
}
public class Main {
    static String render(Report report, Formatter formatter) {
        return formatter.format(report);
    }
    public static void main(String[] args) {
        Report report = new Report.Builder().title("Java").count(@N@).build();
        System.out.println(render(report, new Plain()));
        System.out.println(render(report, new Brackets()));
    }
}
''', lambda n: f'Java:{n}\n[Java={n}]'),
15: ('''Разделите приложение на Model, View и Controller. Model хранит список задач,
add(String) игнорирует пустую строку, count() возвращает размер. View.render(int)
возвращает «Задач: X». Controller получает model и view через конструктор,
обрабатывает add и возвращает show(). В main добавьте «Java» N раз и пустую строку.
Выведите show(), затем count(). Модель не печатает, представление не меняет модель.''', '''
import java.util.ArrayList;
import java.util.List;
class Model {
    private final List<String> tasks = new ArrayList<>();
    void add(String title) {
        if (!title.isEmpty()) {
            tasks.add(title);
        }
    }
    int count() {
        return tasks.size();
    }
}
class View {
    String render(int count) {
        return "Задач: " + count;
    }
}
class Controller {
    private final Model model;
    private final View view;
    Controller(Model model, View view) {
        this.model = model;
        this.view = view;
    }
    void add(String title) { model.add(title); }
    String show() { return view.render(model.count()); }
}
public class Main {
    public static void main(String[] args) {
        Model model = new Model();
        Controller controller = new Controller(model, new View());
        for (int i = 0; i < @N@; i++) {
            controller.add("Java");
        }
        controller.add("");
        System.out.println(controller.show());
        System.out.println(model.count());
    }
}
''', lambda n: f'Задач: {n}\n{n}'),
16: ('''Без БД смоделируйте JOIN, GROUP BY и HAVING. users связывает 1→«Аня», 2→«Борис».
Заказы: (userId=1, amount=N*100), (1,50), (2,70), (3,500). Создайте класс Order.
Игнорируйте заказы без пользователя (INNER JOIN), сгруппируйте суммы по userId.
Оставьте группы с суммой >= 150. Выведите имя:сумма в порядке userId, затем
«Групп: X». Используйте Map и цикл, а не готовый результат SQL.''', '''
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
class Order {
    final int userId;
    final int amount;
    Order(int userId, int amount) {
        this.userId = userId;
        this.amount = amount;
    }
}
public class Main {
    public static void main(String[] args) {
        Map<Integer, String> users = Map.of(1, "Аня", 2, "Борис");
        List<Order> orders = List.of(new Order(1, @N@ * 100),
                new Order(1, 50), new Order(2, 70), new Order(3, 500));
        Map<Integer, Integer> totals = new TreeMap<>();
        for (Order order : orders) {
            if (users.containsKey(order.userId)) {
                int previous = totals.getOrDefault(order.userId, 0);
                totals.put(order.userId, previous + order.amount);
            }
        }
        int groups = 0;
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            if (entry.getValue() >= 150) {
                String name = users.get(entry.getKey());
                System.out.println(name + ":" + entry.getValue());
                groups++;
            }
        }
        System.out.println("Групп: " + groups);
    }
}
''', lambda n: f'Аня:{n*100+50}\nГрупп: 1'),
17: ('''Без Spring-библиотек смоделируйте constructor injection и маршрутизацию.
Интерфейс Greeting имеет hello(String), RussianGreeting возвращает «Привет, имя».
Controller получает Greeting через конструктор; handle(method,path) поддерживает
GET /hello/Ира и возвращает «200 » + приветствие; неизвестный маршрут → «404».
В main выполните GET /hello/Ира N раз, затем POST /hello/Ира один раз и выведите ответы.
Контроллер не создаёт сервис самостоятельно.''', '''
interface Greeting {
    String hello(String name);
}
class RussianGreeting implements Greeting {
    public String hello(String name) {
        return "Привет, " + name;
    }
}
class Controller {
    private final Greeting greeting;
    Controller(Greeting greeting) {
        this.greeting = greeting;
    }
    String handle(String method, String path) {
        if (!method.equals("GET")) {
            return "404";
        }
        if (!path.startsWith("/hello/")) {
            return "404";
        }
        String name = path.substring(7);
        return "200 " + greeting.hello(name);
    }
}
public class Main {
    public static void main(String[] args) {
        Greeting service = new RussianGreeting();
        Controller controller = new Controller(service);
        for (int i = 0; i < @N@; i++) {
            System.out.println(controller.handle("GET", "/hello/Ира"));
        }
        System.out.println(controller.handle("POST", "/hello/Ира"));
    }
}
''', lambda n: '\n'.join(['200 Привет, Ира']*n+['404'])),
18: ('''Без JPA смоделируйте связь Customer–Order и репозиторий. Customer хранит id и
List<Order>, Order — amount и enum Status {NEW,DONE}. Repository хранит клиентов
в Map и возвращает Optional<Customer> из find(int). Клиент 1 имеет заказы
(N*10, DONE), (7, NEW), (5, DONE). Выведите сумму только DONE для клиента 1,
затем наличие клиента 99 (boolean). Поддерживайте связь через Customer.add(Order).''', '''
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
enum Status { NEW, DONE }
class Order {
    final int amount;
    final Status status;
    Order(int amount, Status status) {
        this.amount = amount;
        this.status = status;
    }
}
class Customer {
    final int id;
    final List<Order> orders = new ArrayList<>();
    Customer(int id) { this.id = id; }
    void add(Order order) { orders.add(order); }
    int doneTotal() {
        int total = 0;
        for (Order order : orders) {
            if (order.status == Status.DONE) total += order.amount;
        }
        return total;
    }
}
class Repository {
    private final Map<Integer, Customer> data = new HashMap<>();
    void save(Customer c) { data.put(c.id, c); }
    Optional<Customer> find(int id) { return Optional.ofNullable(data.get(id)); }
}
public class Main {
    public static void main(String[] args) {
        Customer customer = new Customer(1);
        customer.add(new Order(@N@ * 10, Status.DONE));
        customer.add(new Order(7, Status.NEW));
        customer.add(new Order(5, Status.DONE));
        Repository repository = new Repository();
        repository.save(customer);
        System.out.println(repository.find(1).orElseThrow().doneTotal());
        System.out.println(repository.find(99).isPresent());
    }
}
''', lambda n: f'{n*10+5}\nfalse'),
19: ('''Без веб-сервера создайте Response(status,body), Repository с Map {1:«Аня»} и
Controller с внедрённым репозиторием. get(String id) возвращает 200 и имя для
существующего положительного id; 404 и «Нет» для отсутствующего; 400 и «Ошибка»
для нечислового или неположительного id. В main запросите "1", "N+1", "oops", "0".
Выведите каждую пару status body одной строкой. Перехватывайте NumberFormatException.''', '''
import java.util.Map;
record Response(int status, String body) {}
class Repository {
    private final Map<Integer, String> users = Map.of(1, "Аня");
    String find(int id) {
        return users.get(id);
    }
}
class Controller {
    private final Repository repository;
    Controller(Repository repository) {
        this.repository = repository;
    }
    Response get(String text) {
        try {
            int id = Integer.parseInt(text);
            if (id <= 0) {
                return new Response(400, "Ошибка");
            }
            String user = repository.find(id);
            if (user == null) {
                return new Response(404, "Нет");
            }
            return new Response(200, user);
        } catch (NumberFormatException e) {
            return new Response(400, "Ошибка");
        }
    }
}
public class Main {
    public static void main(String[] args) {
        Controller controller = new Controller(new Repository());
        String[] ids = {"1", String.valueOf(@N@ + 1), "oops", "0"};
        for (String id : ids) {
            Response response = controller.get(id);
            System.out.println(response.status() + " " + response.body());
        }
    }
}
''', lambda n: '200 Аня\n404 Нет\n400 Ошибка\n400 Ошибка'),
20: ('''Без JUnit/Mockito создайте Repository.find(int), FakeRepository со счётчиком calls
и Service с внедрённым Repository. FakeRepository возвращает «User» + id; Service
отвергает id <= 0 через IllegalArgumentException до вызова репозитория.
Напишите три проверки в стиле Arrange–Act–Assert: результат find(N), один вызов fake,
исключение для 0 без дополнительного вызова fake. При ошибке бросайте AssertionError.
При успехе выведите «PASS 3», затем фактическое число вызовов репозитория.''', '''
interface Repository {
    String find(int id);
}
class FakeRepository implements Repository {
    int calls;
    public String find(int id) {
        calls++;
        return "User" + id;
    }
}
class Service {
    private final Repository repository;
    Service(Repository repository) { this.repository = repository; }
    String find(int id) {
        if (id <= 0) throw new IllegalArgumentException("id");
        return repository.find(id);
    }
}
public class Main {
    static void check(boolean condition) {
        if (!condition) throw new AssertionError("Проверка не пройдена");
    }
    public static void main(String[] args) {
        FakeRepository fake = new FakeRepository();
        Service service = new Service(fake);
        String actual = service.find(@N@);
        check(actual.equals("User" + @N@));
        check(fake.calls == 1);
        boolean thrown = false;
        try {
            service.find(0);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        check(thrown && fake.calls == 1);
        System.out.println("PASS 3");
        System.out.println(fake.calls);
    }
}
''', lambda n: 'PASS 3\n1'),
21: ('''Смоделируйте Kanban-доску с WIP-лимитом N. Board хранит todo={A,B,C}, doing и done.
start() переносит первую todo в doing, если doing.size() < limit и todo не пуст;
иначе возвращает false. finish() переносит первую doing в done, если она существует.
В main вызовите start N+1 раз и выведите результаты, затем finish один раз и start
ещё раз (его результат тоже выведите). В конце выведите размеры todo, doing, done
как «todo/doing/done». Не теряйте задачи при достижении лимита.''', '''
import java.util.ArrayList;
import java.util.List;
class Board {
    final List<String> todo = new ArrayList<>(List.of("A", "B", "C"));
    final List<String> doing = new ArrayList<>();
    final List<String> done = new ArrayList<>();
    private final int limit;
    Board(int limit) {
        this.limit = limit;
    }
    boolean start() {
        if (doing.size() >= limit || todo.isEmpty()) {
            return false;
        }
        doing.add(todo.remove(0));
        return true;
    }
    void finish() {
        if (!doing.isEmpty()) {
            done.add(doing.remove(0));
        }
    }
    String summary() {
        return todo.size() + "/" + doing.size() + "/" + done.size();
    }
}
public class Main {
    public static void main(String[] args) {
        int limit = @N@;
        Board board = new Board(limit);
        for (int i = 0; i < limit + 1; i++) {
            System.out.println(board.start());
        }
        board.finish();
        System.out.println(board.start());
        System.out.println(board.summary());
    }
}
''', lambda n: '\n'.join(['true']*min(n,3)+['false']*(n+1-min(n,3))+['true' if n<3 else 'false',f'{max(0,2-n)}/{min(n,2)}/1'])),
22: ('''Создайте Stage(name,success) и Pipeline.run(List<Stage>). Пайплайн печатает имя
каждого реально запущенного этапа, при первом false печатает «STOP имя» и немедленно
возвращает false; если все этапы успешны — печатает «DEPLOYED» и возвращает true.
Этапы build=true, test=(N % 2 == 0), deploy=true. Выведите результат run последней
строкой. Отдельный метод validate проверяет порядок build,test,deploy и бросает
IllegalArgumentException при нарушении. Вызовите его перед запуском.''', '''
import java.util.List;
record Stage(String name, boolean success) {}
class Pipeline {
    void validate(List<Stage> stages) {
        List<String> expected = List.of("build", "test", "deploy");
        if (stages.size() != expected.size()) {
            throw new IllegalArgumentException("Этапы");
        }
        for (int i = 0; i < stages.size(); i++) {
            if (!stages.get(i).name().equals(expected.get(i))) {
                throw new IllegalArgumentException("Порядок");
            }
        }
    }
    boolean run(List<Stage> stages) {
        validate(stages);
        for (Stage stage : stages) {
            System.out.println(stage.name());
            if (!stage.success()) {
                System.out.println("STOP " + stage.name());
                return false;
            }
        }
        System.out.println("DEPLOYED");
        return true;
    }
}
public class Main {
    public static void main(String[] args) {
        int n = @N@;
        List<Stage> stages = List.of(new Stage("build", true),
                new Stage("test", n % 2 == 0), new Stage("deploy", true));
        Pipeline pipeline = new Pipeline();
        System.out.println(pipeline.run(stages));
    }
}
''', lambda n: 'build\ntest\n'+('deploy\nDEPLOYED\ntrue' if n%2==0 else 'STOP test\nfalse')),
}


def project(number, scenario):
    prompt, source, output = PROJECTS[number]
    return re.sub(r'\bN\b', str(scenario), prompt), dedent(source).replace('@N@', str(scenario)).strip() + '\n', output(scenario)
