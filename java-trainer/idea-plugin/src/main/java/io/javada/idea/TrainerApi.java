package io.javada.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** HTTP contract with the existing, separately started Flask server. */
final class TrainerApi {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    // Never accept a remote URL, even from an accidentally copied IDE setting:
    // /api/compile executes the user's code and /api/progress is private.
    static URI address(int port, String path) {
        if (port < 1 || port > 65535 || !path.startsWith("/api/")) {
            throw new IllegalArgumentException("Укажите порт локального сервера (1–65535).");
        }
        return URI.create("http://127.0.0.1:" + port + path);
    }

    record CodingTask(String id, int moduleId, String moduleTitle, String section,
                      String lessonTitle, String prompt, String hint, String starter,
                      String mode, String sampleInput, String sampleOutput) {
        @Override public String toString() {
            return id + " · " + lessonTitle;
        }
    }

    record Profile(int xp, Set<String> awarded, Set<String> labs, Set<String> badges,
                   Map<String, String> drafts) {
        boolean solved(CodingTask task) {
            return awarded.contains(task.id()) || labs.contains(task.id());
        }
    }

    record CompileResult(boolean success, String kind, String output, String error,
                         Profile progress) { }

    List<CodingTask> catalog(int port) throws IOException, InterruptedException {
        JsonArray entries = get(port, "/api/ide/tasks").getAsJsonArray("tasks");
        if (entries == null) throw new IOException("Сервер вернул каталог без задач. Обновите Flask-приложение.");
        List<CodingTask> result = new ArrayList<>();
        for (JsonElement element : entries) {
            JsonObject item = element.getAsJsonObject();
            result.add(new CodingTask(
                    text(item, "id"), number(item, "moduleId"), text(item, "moduleTitle"),
                    text(item, "section"), text(item, "lessonTitle"), text(item, "prompt"),
                    text(item, "hint"), text(item, "starterCode"), text(item, "mode"),
                    text(item, "sampleInput"), text(item, "sampleOutput")));
        }
        return result;
    }

    Profile profile(int port) throws IOException, InterruptedException {
        return parseProfile(get(port, "/api/progress"));
    }

    Profile saveDraft(int port, String taskId, String code) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("taskId", taskId);
        body.addProperty("code", code);
        return parseProfile(post(port, "/api/progress/draft", body));
    }

    CompileResult check(int port, CodingTask task, String code) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("taskType", task.mode());
        body.addProperty("taskId", task.id());
        body.addProperty("recordProgress", true);
        JsonObject answer = post(port, "/api/compile", body);
        if (!answer.has("success") || !answer.has("kind")) {
            throw new IOException("Сервер вернул неполный ответ без success/kind.");
        }
        Profile progress = answer.has("progress") ? parseProfile(answer.getAsJsonObject("progress")) : null;
        return new CompileResult(answer.get("success").getAsBoolean(),
                text(answer, "kind"), text(answer, "output"), text(answer, "error"), progress);
    }

    private JsonObject get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address(port, path))
                .timeout(Duration.ofSeconds(8)).GET().build();
        return send(request);
    }

    private JsonObject post(int port, String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address(port, path))
                .timeout(Duration.ofSeconds(30)) // javac 10s + Java 5s + overhead
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return send(request);
    }

    static class ServerException extends IOException {
        private final int statusCode;
        private final String kind;

        ServerException(int statusCode, String kind, String message) {
            super(message);
            this.statusCode = statusCode;
            this.kind = kind;
        }

        int statusCode() { return statusCode; }
        String kind() { return kind; }
    }

    private JsonObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body;
        try {
            body = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException badJson) {
            throw new IOException("Сервер ответил не JSON. Проверьте адрес и версию JavaДа.", badJson);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String msg = text(body, "error").isBlank() ? "Ошибка HTTP " + response.statusCode() : text(body, "error");
            throw new ServerException(response.statusCode(), text(body, "kind"), msg);
        }
        return body;
    }

    private static Profile parseProfile(JsonObject profile) throws IOException {
        if (profile == null || !profile.has("state")) throw new IOException("Нет сохранённого прогресса в ответе.");
        JsonObject state = profile.getAsJsonObject("state");
        Map<String, String> drafts = new HashMap<>();
        if (profile.has("drafts") && profile.get("drafts").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : profile.getAsJsonObject("drafts").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    String code = text(entry.getValue().getAsJsonObject(), "code");
                    if (!code.isEmpty()) drafts.put(entry.getKey(), code);
                }
            }
        }
        return new Profile(number(state, "xp"), ids(state, "awardedTasks"),
                ids(state, "completedLabs"), ids(state, "achievements"), drafts);
    }

    private static Set<String> ids(JsonObject json, String key) {
        Set<String> result = new HashSet<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement item : json.getAsJsonArray(key)) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) result.add(item.getAsString());
            }
        }
        return result;
    }

    private static int number(JsonObject json, String key) {
        try { return json.has(key) ? json.get(key).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    private static String text(JsonObject json, String key) {
        try { return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }
}
