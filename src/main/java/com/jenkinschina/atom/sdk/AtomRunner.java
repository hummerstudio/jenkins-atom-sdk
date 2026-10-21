package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Entry point an atom jar calls: {@code java -cp atom.jar com.jenkinschina.atom.sdk.AtomRunner}.
 *
 * <p>Contract (identical to BK-CI, so the same runner code path works for both):
 * <ul>
 *   <li>reads {@code $bk_data_input} (default {@code input.json}) in {@code $bk_data_dir};
 *   <li>reads {@code .secrets.json} when present — the runner resolves Jenkins credentials before launch;
 *   <li>writes {@code $bk_data_output} (default {@code output.json}) with status / message / errorType / data;
 *   <li>deletes {@code .secrets.json} once the task has run, even when it throws.
 * </ul>
 */
public final class AtomRunner {

    private AtomRunner() {}

    public static void main(String[] args) {
        Path dir = Paths.get(env("bk_data_dir", "."));
        String inputName = env("bk_data_input", "input.json");
        String outputName = env("bk_data_output", "output.json");
        int code = run(dir, inputName, outputName, System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** Runs one atom; returns 0 on success, 1 otherwise. Never throws. */
    public static int run(Path dir, String inputName, String outputName, PrintStream out) {
        Path secretFile = dir.resolve(".secrets.json");
        AtomResult result;
        try {
            Map<String, Object> params = readParams(dir.resolve(inputName));
            Map<String, String> secrets = readSecrets(secretFile);
            AtomTask task = locateTask(out);
            Path workspace = Paths.get(params.get("bkWorkspace") instanceof String s
                    ? s
                    : String.valueOf(params.getOrDefault("bk_workspace", dir.toString())));
            AtomContext context = new AtomContext(params, secrets, dir, workspace, out);
            result = task.run(context);
            if (result == null) {
                result = AtomResult.failure("atom returned null instead of an AtomResult");
            }
        } catch (Throwable t) {
            out.println("[atom] ERROR " + t);
            t.printStackTrace(out);
            result = AtomResult.error(ErrorType.PLUGIN, 1, t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            try {
                Files.deleteIfExists(secretFile);
            } catch (IOException ignored) {
                // best effort: the runner deletes it again on cleanup
            }
        }

        try {
            Files.writeString(
                    dir.resolve(outputName),
                    Json.write(result.toJson()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("[atom] cannot write " + outputName + ": " + e);
            return 1;
        }
        out.println("[atom] status=" + result.getStatus()
                + (result.getMessage() == null ? "" : " message=" + result.getMessage()));
        return result.isSuccess() ? 0 : 1;
    }

    private static AtomTask locateTask(PrintStream out) {
        String explicit = System.getProperty("atom.task.class");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("ATOM_TASK_CLASS");
        }
        if (explicit != null && !explicit.isBlank()) {
            return instantiate(explicit);
        }
        Iterator<AtomTask> iterator = ServiceLoader.load(AtomTask.class).iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException(
                    "no AtomTask found: add META-INF/services/com.jenkinschina.atom.sdk.AtomTask"
                            + " or set ATOM_TASK_CLASS");
        }
        AtomTask task = iterator.next();
        out.println("[atom] task " + task.getClass().getName());
        return task;
    }

    private static AtomTask instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            return (AtomTask) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + className + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> readParams(Path file) {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> readSecrets(Path file) {
        Map<String, String> secrets = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return secrets;
        }
        try {
            Json.parseObject(Files.readString(file, StandardCharsets.UTF_8))
                    .forEach((key, value) -> {
                        if (value != null) {
                            secrets.put(key, String.valueOf(value));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return secrets;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
