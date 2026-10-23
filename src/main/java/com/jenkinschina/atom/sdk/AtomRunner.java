package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Picks the implementation to run. A jar may contain several, so the order is:
     *
     * <ol>
     *   <li>{@code atom.task.class} / {@code ATOM_TASK_CLASS}: an explicit class name, always wins;
     *   <li>{@code atom.code} / {@code ATOM_CODE} / {@code BK_CI_ATOM_CODE}: the task whose
     *       {@link AtomService#value()} matches, which is how a multi-atom jar stays self-describing;
     *   <li>exactly one provider in the jar: that one.
     * </ol>
     *
     * <p>Anything ambiguous fails loudly with the candidate list — silently running "the first" is how a jar with
     * several atoms ends up executing the wrong one.
     */
    private static AtomTask locateTask(PrintStream out) {
        String explicit = propertyOrEnv("atom.task.class", "ATOM_TASK_CLASS");
        if (explicit != null) {
            AtomTask task = instantiate(explicit);
            out.println("[atom] task " + task.getClass().getName() + " (ATOM_TASK_CLASS)");
            return task;
        }

        List<AtomTask> providers = new ArrayList<>();
        for (AtomTask candidate : ServiceLoader.load(AtomTask.class)) {
            providers.add(candidate);
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "no AtomTask found: add META-INF/services/com.jenkinschina.atom.sdk.AtomTask"
                            + " or set ATOM_TASK_CLASS");
        }

        String atomCode = propertyOrEnv("atom.code", "ATOM_CODE", "BK_CI_ATOM_CODE");
        if (atomCode != null) {
            List<AtomTask> matches = new ArrayList<>();
            for (AtomTask candidate : providers) {
                if (atomCode.equals(codeOf(candidate))) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1) {
                out.println("[atom] task " + matches.get(0).getClass().getName() + " (atomCode=" + atomCode + ")");
                return matches.get(0);
            }
            if (matches.size() > 1) {
                throw new IllegalStateException("several AtomTask implementations claim atomCode='" + atomCode
                        + "': " + names(matches) + "; disambiguate with ATOM_TASK_CLASS");
            }
        }

        if (providers.size() == 1) {
            out.println("[atom] task " + providers.get(0).getClass().getName());
            return providers.get(0);
        }
        throw new IllegalStateException("this jar has " + providers.size() + " AtomTask implementations ("
                + names(providers) + ") and none matched atomCode=" + (atomCode == null ? "(unset)" : atomCode)
                + "; set x-jenkins.taskClass in task.json or ATOM_TASK_CLASS");
    }

    /** {@code @AtomService("code")}, or the class name when the annotation does not declare one. */
    private static String codeOf(AtomTask task) {
        AtomService service = task.getClass().getAnnotation(AtomService.class);
        if (service != null && !service.value().isBlank()) {
            return service.value();
        }
        return task.getClass().getName();
    }

    private static String names(List<AtomTask> tasks) {
        List<String> names = new ArrayList<>();
        for (AtomTask task : tasks) {
            names.add(task.getClass().getName() + "(" + codeOf(task) + ")");
        }
        return String.join(", ", names);
    }

    private static String propertyOrEnv(String property, String... envNames) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) {
            return value;
        }
        for (String envName : envNames) {
            String fromEnv = System.getenv(envName);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv;
            }
        }
        return null;
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
