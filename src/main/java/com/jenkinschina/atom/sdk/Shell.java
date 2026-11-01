package com.jenkinschina.atom.sdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Run external commands and scripts from an atom.
 *
 * <p>Output goes straight to the atom's stdout/stderr, so the runner captures it in the build log and applies the same
 * redaction as everything else. Exit codes are returned, never swallowed: {@link #check(int)} turns a non-zero code
 * into a failure with the command in the message.
 *
 * <pre>{@code
 * Shell.check(Shell.sh("tar -czf out.tgz dist"));   // short command, /bin/sh -c
 * String out = Shell.capture("git", "rev-parse", "HEAD");
 * }</pre>
 *
 * <p>For anything longer than a line, use {@link #run(Script)}: the script is written to a file and handed to the
 * interpreter, so quoting survives and a long script does not hit the argv limit that {@code sh -c} runs into.
 *
 * <pre>{@code
 * Shell.Result r = Shell.run(Script.builder()
 *         .interpreter(Interpreter.PYTHON)
 *         .script("import sys\nprint(sys.version)\n")
 *         .env("APP_ENV", "staging")
 *         .workdir(workspace.resolve("svc"))
 *         .timeout(Duration.ofMinutes(10))
 *         .failOnError(false)
 *         .build());
 * if (!r.ok()) { ... r.exitCode(), r.output() ... }
 * }</pre>
 */
public final class Shell {

    /** How much of the output tail is kept for {@link Result#output()}; the build log itself is unbounded. */
    private static final int TAIL_BYTES = 256 * 1024;

    private Shell() {
    }

    /** {@code /bin/sh -c script}. */
    public static int sh(String script) {
        return run("/bin/sh", "-c", script);
    }

    /** {@code bash -c script}, for scripts that need bashisms. */
    public static int bash(String script) {
        return run("bash", "-c", script);
    }

    /** {@code cmd.exe /c script}. */
    public static int bat(String script) {
        return run("cmd.exe", "/c", script);
    }

    /** {@code powershell -NoProfile -NonInteractive -Command script}. */
    public static int powershell(String script) {
        return run("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }

    /** Run a command in the current working directory, streaming its output to the build log. */
    public static int run(String... command) {
        return run(null, command);
    }

    /** Run a command in {@code workingDir} (null = the atom's working directory), streaming output. */
    public static int run(Path workingDir, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).inheritIO();
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            return builder.start().waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("cannot run " + String.join(" ", command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running " + String.join(" ", command), e);
        }
    }

    /** Run a command and return its merged stdout/stderr instead of streaming it. */
    public static String capture(String... command) {
        return capture(null, command);
    }

    /** Run a command in {@code workingDir} and return its merged stdout/stderr. */
    public static String capture(Path workingDir, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("command " + String.join(" ", command) + " exited with " + code
                        + (output.isBlank() ? "" : ": " + output.strip()));
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("cannot run " + String.join(" ", command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running " + String.join(" ", command), e);
        }
    }

    /** Fail the atom when a command did not succeed, quoting the command for the log. */
    public static void check(int exitCode, String... command) {
        if (exitCode != 0) {
            List<String> parts = new ArrayList<>(List.of(command));
            throw new IllegalStateException("command failed with exit code " + exitCode + ": " + String.join(" ", parts));
        }
    }

    /** Fail the atom when the script did not succeed, quoting the exit code and the tail of its output. */
    public static void check(Result result) {
        if (!result.ok()) {
            throw new IllegalStateException(result.describe());
        }
    }

    /** The program a script is handed to. */
    public enum Interpreter {
        SH("sh", "sh"),
        BASH("bash", "bash"),
        BAT("cmd.exe", "bat"),
        POWERSHELL("powershell", "powershell"),
        PWSH("pwsh", "pwsh"),
        PYTHON("python3", "python"),
        NODE("node", "node"),
        GO("go", "go");

        private final String defaultBinary;
        private final String toolName;

        Interpreter(String defaultBinary, String toolName) {
            this.defaultBinary = defaultBinary;
            this.toolName = toolName;
        }

        /** The command looked up on PATH when the caller names no path. */
        public String defaultBinary() {
            return defaultBinary;
        }

        /** The name used for {@code atom_<tool>_path} and in messages. */
        public String toolName() {
            return toolName;
        }

        /** Parse leniently: {@code python}, {@code python3}, {@code py}, {@code nodejs} and {@code powershell7} work. */
        public static Interpreter of(String value) {
            String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (key.startsWith("python") || key.equals("py")) {
                return PYTHON;
            }
            if (key.startsWith("node") || key.equals("js") || key.equals("javascript")) {
                return NODE;
            }
            if (key.equals("pwsh") || key.equals("powershell7") || key.equals("powershell-core")) {
                return PWSH;
            }
            for (Interpreter candidate : values()) {
                if (candidate.name().equalsIgnoreCase(key) || candidate.defaultBinary.equals(key)
                        || candidate.toolName.equals(key)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("unknown interpreter '" + value + "'; use one of"
                    + " sh, bash, bat, powershell, pwsh, python, node, go");
        }
    }

    /** A script to run: interpreter, text or file, arguments, environment, working directory, timeout. */
    public static final class Script {
        private final Interpreter interpreter;
        private final String text;
        private final Path file;
        private final String interpreterPath;
        private final List<String> args;
        private final Map<String, String> env;
        private final Path workdir;
        private final Duration timeout;
        private final boolean failOnError;

        private Script(Builder builder) {
            this.interpreter = builder.interpreter;
            this.text = builder.text;
            this.file = builder.file;
            this.interpreterPath = builder.interpreterPath;
            this.args = List.copyOf(builder.args);
            this.env = Map.copyOf(builder.env);
            this.workdir = builder.workdir;
            this.timeout = builder.timeout;
            this.failOnError = builder.failOnError;
        }

        public static Builder builder() {
            return new Builder();
        }

        /** A shell script with no other options: {@code Script.of("make -j4")}. */
        public static Script of(String text) {
            return builder().script(text).build();
        }

        public Interpreter interpreter() {
            return interpreter;
        }

        public boolean failOnError() {
            return failOnError;
        }

        public static final class Builder {
            private Interpreter interpreter = Interpreter.SH;
            private String text;
            private Path file;
            private String interpreterPath;
            private final List<String> args = new ArrayList<>();
            private final Map<String, String> env = new LinkedHashMap<>();
            private Path workdir;
            private Duration timeout = Duration.ofMinutes(30);
            private boolean failOnError = true;

            public Builder interpreter(Interpreter value) {
                this.interpreter = value == null ? Interpreter.SH : value;
                return this;
            }

            /** The script itself. Mutually exclusive with {@link #file(Path)}. */
            public Builder script(String value) {
                this.text = value;
                return this;
            }

            /** A script already on disk. */
            public Builder file(Path value) {
                this.file = value;
                return this;
            }

            /** Pin the interpreter, e.g. a venv's python or a specific Node; wins over the environment and PATH. */
            public Builder interpreterPath(String value) {
                this.interpreterPath = value == null || value.isBlank() ? null : value.trim();
                return this;
            }

            public Builder args(String... values) {
                if (values != null) {
                    for (String value : values) {
                        if (value != null) {
                            args.add(value);
                        }
                    }
                }
                return this;
            }

            public Builder args(List<String> values) {
                if (values != null) {
                    args.addAll(values);
                }
                return this;
            }

            /** An environment variable for this execution only; the atom's own environment is untouched. */
            public Builder env(String name, String value) {
                if (name != null && !name.isBlank() && value != null) {
                    env.put(name, value);
                }
                return this;
            }

            public Builder env(Map<String, String> values) {
                if (values != null) {
                    values.forEach(this::env);
                }
                return this;
            }

            public Builder workdir(Path value) {
                this.workdir = value;
                return this;
            }

            public Builder timeout(Duration value) {
                if (value != null && !value.isNegative() && !value.isZero()) {
                    this.timeout = value;
                }
                return this;
            }

            public Builder failOnError(boolean value) {
                this.failOnError = value;
                return this;
            }

            public Script build() {
                if ((text == null || text.isBlank()) && file == null) {
                    throw new IllegalArgumentException("script or file is required");
                }
                return new Script(this);
            }
        }
    }

    /** What a script left behind. */
    public static final class Result {
        private final int exitCode;
        private final String output;
        private final long durationMs;
        private final Path interpreter;
        private final boolean timedOut;
        private final List<String> command;

        Result(int exitCode, String output, long durationMs, Path interpreter, boolean timedOut, List<String> command) {
            this.exitCode = exitCode;
            this.output = output;
            this.durationMs = durationMs;
            this.interpreter = interpreter;
            this.timedOut = timedOut;
            this.command = List.copyOf(command);
        }

        public int exitCode() {
            return exitCode;
        }

        /** The tail of the merged stdout/stderr (at most 256 KB), for outputs and reports. */
        public String output() {
            return output;
        }

        public long durationMs() {
            return durationMs;
        }

        /** The interpreter that actually ran, after path resolution. */
        public Path interpreter() {
            return interpreter;
        }

        public boolean timedOut() {
            return timedOut;
        }

        public boolean ok() {
            return exitCode == 0 && !timedOut;
        }

        /** The command line, for the log; the script body is left out on purpose (it can carry secrets). */
        public List<String> command() {
            return command;
        }

        /** A one-line explanation for an atom's failure message. */
        public String describe() {
            if (timedOut) {
                return "script timed out after " + durationMs + " ms: " + String.join(" ", command);
            }
            return "script exited with " + exitCode + ": " + String.join(" ", command)
                    + (output.isBlank() ? "" : "\n" + output.strip());
        }
    }

    /** Run a script, streaming its output to the build log and returning what it left behind. */
    public static Result run(Script script) {
        Path temp = null;
        try {
            Path interpreter = resolve(script.interpreter, script.interpreterPath);
            List<String> command = new ArrayList<>();
            command.add(interpreter.toString());

            Path body = script.file;
            if (body == null) {
                // the script goes to a file rather than into -c: quoting survives, and long scripts do not hit argv limits
                temp = Files.createTempFile("atom-script-", suffix(script.interpreter));
                Files.writeString(temp, script.text, StandardCharsets.UTF_8);
                temp.toFile().deleteOnExit();
                body = temp;
            }
            Path workingDir = script.workdir;
            Path absolute = body.isAbsolute() ? body
                    : (workingDir == null ? body.toAbsolutePath() : workingDir.resolve(body).toAbsolutePath());

            switch (script.interpreter) {
                case BAT -> command.add("/c");
                case POWERSHELL -> {
                    command.add("-NoProfile");
                    command.add("-NonInteractive");
                    command.add("-File");
                }
                case PWSH -> {
                    command.add("-NoProfile");
                    command.add("-File");
                }
                case GO -> command.add("run");
                default -> { }
            }
            command.add(absolute.toString());
            command.addAll(script.args);

            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.environment().putAll(script.env);

            long started = System.nanoTime();
            Process process = builder.start();
            Tail tail = new Tail(TAIL_BYTES);
            Thread pump = new Thread(() -> pump(process.getInputStream(), tail), "atom-script-output");
            pump.setDaemon(true);
            pump.start();

            boolean finished = process.waitFor(script.timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            pump.join(2000);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;

            Result result = new Result(finished ? process.exitValue() : -1, tail.text(), elapsed, interpreter,
                    !finished, command);
            if (script.failOnError && !result.ok()) {
                throw new IllegalStateException(result.describe());
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("cannot run script: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running script", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // a leftover temp file is not worth failing the atom over
                }
            }
        }
    }

    /** Find a program the way the build would: explicit path, then {@code atom_<tool>_path} (by the caller), then PATH. */
    public static Path which(String binary) {
        if (binary == null || binary.isBlank()) {
            return null;
        }
        Path direct = Path.of(binary);
        if (direct.isAbsolute() || binary.contains("/") || binary.contains("\\")) {
            return Files.isExecutable(direct) ? direct : null;
        }
        String[] extensions = isWindows() ? new String[] {"", ".exe", ".cmd", ".bat"} : new String[] {""};
        for (String dir : searchPath()) {
            for (String extension : extensions) {
                Path candidate = Path.of(dir, binary + extension);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Path resolve(Interpreter interpreter, String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path pinned = which(explicitPath);
            if (pinned == null) {
                throw new IllegalStateException("interpreter not found: " + explicitPath
                        + " (a path was given but is not executable)");
            }
            return pinned;
        }
        String injected = System.getenv("atom_" + interpreter.toolName() + "_path");
        if (injected != null && !injected.isBlank()) {
            Path fromRunner = which(injected);
            if (fromRunner != null) {
                return fromRunner;
            }
        }
        Path found = which(interpreter.defaultBinary());
        if (found == null) {
            throw new IllegalStateException(interpreter.defaultBinary() + " not found on PATH; install it or set"
                    + " interpreterPath (searched: " + String.join(", ", searchPath()) + ")");
        }
        return found;
    }

    private static List<String> searchPath() {
        String path = System.getenv("PATH");
        List<String> dirs = new ArrayList<>();
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) {
                    dirs.add(dir);
                }
            }
        }
        if (dirs.isEmpty()) {
            dirs.addAll(List.of("/usr/local/bin", "/usr/bin", "/bin"));
        }
        return dirs;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static String suffix(Interpreter interpreter) {
        return switch (interpreter) {
            case BAT -> ".bat";
            case POWERSHELL, PWSH -> ".ps1";
            case PYTHON -> ".py";
            case NODE -> ".js";
            case GO -> ".go";
            default -> ".sh";
        };
    }

    /** Copies the process output to our stdout and keeps the tail for {@link Result#output()}. */
    private static void pump(InputStream in, Tail tail) {
        byte[] buffer = new byte[8192];
        OutputStream out = System.out;
        try {
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                out.flush();
                tail.append(buffer, read);
            }
        } catch (IOException e) {
            // the process died; whatever arrived is what we report
        }
    }

    /** A ring buffer holding the tail of a stream. */
    private static final class Tail {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final int limit;

        Tail(int limit) {
            this.limit = limit;
        }

        synchronized void append(byte[] bytes, int length) {
            buffer.write(bytes, 0, length);
            if (buffer.size() > limit) {
                byte[] all = buffer.toByteArray();
                buffer.reset();
                buffer.write(all, all.length - limit, limit);
            }
        }

        synchronized String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
