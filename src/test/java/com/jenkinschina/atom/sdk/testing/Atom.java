package com.jenkinschina.atom.sdk.testing;

import com.jenkinschina.atom.sdk.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Runs an atom the way the runner does, for tests: a throwaway directory, {@code input.json} merged out of the build
 * environment and the case's own inputs, then the packaged jar started with {@code -cp <jar>} and the three environment
 * variables the contract names.
 *
 * <p>This is the Java spelling of what the atoms used to do in {@code tools/smoke.sh} — same environment, same
 * arguments, same assertions, but inside JUnit where a failure is a test result instead of a line of shell output.
 *
 * <pre>{@code
 * AtomResult r = atom.input("{\"path\": \"notes.txt\", \"content\": \"a\"}").file("notes.txt", "old").run();
 * r.assertOk();
 * assertThat(r.field("created"), is("true"));
 * assertThat(r.file("notes.txt"), is("a"));
 * }</pre>
 */
public final class Atom {

    private final String code;
    private final Path jar;
    private final Path dir;
    private final Map<String, String> inputs = new LinkedHashMap<>();
    private final Map<String, String> env = new LinkedHashMap<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, String> bins = new LinkedHashMap<>();
    private String rawInput;
    private boolean mergeEnvironment = true;

    private Atom(String code, Path jar, Path dir) {
        this.code = code;
        this.jar = jar;
        this.dir = dir;
    }

    /**
     * The atom of the module being built. Failsafe passes {@code atom.module} and {@code target} through system
     * properties, so a test class needs nothing but this call.
     */
    public static Atom of() throws IOException {
        Path module = Path.of(System.getProperty("atom.module", System.getProperty("user.dir")));
        Path scratch = Path.of(System.getProperty("atom.scratch", module.resolve("target").toString()));
        return of(module, scratch);
    }

    /** The atom in the current module: {@code task.json} names it, {@code target/<code>.jar} is the shaded build. */
    public static Atom of(Path moduleDir, Path scratchDir) throws IOException {
        String task = Files.readString(moduleDir.resolve("task.json"), StandardCharsets.UTF_8);
        String code = String.valueOf(((Map<?, ?>) Json.parseObject(task)).get("atomCode"));
        Path jar = moduleDir.resolve("target").resolve(code + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("no " + jar + " — the test runs against the packaged jar, so bind this test "
                    + "to the package phase (failsafe) rather than running it straight from the IDE");
        }
        return new Atom(code, jar, Files.createDirectories(scratchDir));
    }

    /** Put a file into the workspace before the run — a pre-existing log, a repository, a template. */
    public Atom file(String path, String content) {
        files.put(path, content);
        return this;
    }

    /** Put a file of bytes, for the cases where the separator or the encoding is the thing under test. */
    public Atom fileBytes(String path, byte[] content) {
        files.put(path, new String(content, StandardCharsets.ISO_8859_1));
        return this;
    }

    /** The case's {@code input.json}; the build environment is merged underneath it, as the runner does. */
    public Atom input(String json) {
        this.rawInput = json;
        return this;
    }

    /** One input field, for cases where the JSON is noise. */
    public Atom input(String key, String value) {
        inputs.put(key, value);
        return this;
    }

    /**
     * A fake tool on {@code PATH} — {@code bin("mvn", "#!/bin/sh\nexit 7")}. Most atoms shell out, so the case that
     * matters is what they do with what the tool said; this puts the tool under the test's control without a real
     * Maven, git or docker anywhere in sight.
     */
    public Atom bin(String name, String script) {
        bins.put(name, script);
        return this;
    }

    /** An extra environment variable for this run. */
    public Atom env(String key, String value) {
        env.put(key, value);
        return this;
    }

    /** Run without the build environment — for the case that proves the atom does not need it. */
    public Atom withoutEnvironment() {
        this.mergeEnvironment = false;
        return this;
    }

    public AtomResult run() throws IOException, InterruptedException {
        Path work = Files.createDirectories(dir.resolve("run-" + System.nanoTime()));

        Map<String, String> data = new TreeMap<>();
        if (mergeEnvironment) {
            // the runner puts the build environment into input.json: BUILD_NUMBER, JOB_NAME, and the rest
            System.getenv().forEach((k, v) -> {
                if (isUpper(k) || "atom_build_log".equals(k)) {
                    data.put(k, v);
                }
            });
        }
        if (rawInput != null) {
            // the case's inputs win over the environment, as the runner's layered input does
            Json.parseObject(rawInput).forEach((key, value) -> data.put(key, value == null ? "" : String.valueOf(value)));
        }
        data.putAll(inputs);
        data.putIfAbsent("atom_workspace", work.toString());
        Files.writeString(work.resolve("input.json"), Json.write(data), StandardCharsets.UTF_8);

        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = work.resolve(entry.getKey());
            Files.createDirectories(target.getParent() == null ? work : target.getParent());
            Files.write(target, entry.getValue().getBytes(StandardCharsets.UTF_8));
        }

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        // the atom runs in this child JVM, so JaCoCo has to instrument *it*; the test JVM covers none of the atom's code
        String destfile = System.getProperty("jacoco.destfile", "");
        if (!destfile.isEmpty()) {
            String agent = jacocoAgent();
            if (agent != null) {
                command.add("-javaagent:" + agent + "=destfile=" + destfile + ",append=true");
            }
        }
        command.add("-cp");
        command.add(jar.toString());
        command.add("com.jenkinschina.atom.sdk.AtomRunner");

        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        if (!bins.isEmpty()) {
            Path binDir = Files.createDirectories(work.resolve("bin"));
            for (Map.Entry<String, String> entry : bins.entrySet()) {
                Path script = binDir.resolve(entry.getKey());
                Files.writeString(script, entry.getValue(), StandardCharsets.UTF_8);
                script.toFile().setExecutable(true);
            }
            environment.put("PATH", binDir + java.io.File.pathSeparator + environment.getOrDefault("PATH", ""));
        }
        environment.put("atom_data_dir", work.toString());
        environment.put("atom_data_input", "input.json");
        environment.put("atom_data_output", "output.json");
        environment.put("atom_workspace", work.toString());
        environment.putAll(env);

        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(300, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(code + " did not finish in 300s");
        }
        return new AtomResult(code, work, process.exitValue(), new String(output, StandardCharsets.UTF_8));
    }


    /** The JaCoCo agent jar, when the build put one on the test classpath (`org.jacoco.agent:runtime`). */
    private static String jacocoAgent() {
        String explicit = System.getProperty("jacoco.agent.jar", "");
        if (!explicit.isEmpty()) {
            return explicit;
        }
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString();
            if (name.startsWith("org.jacoco.agent") && name.endsWith(".jar")) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isUpper(String key) {
        return !key.isEmpty() && key.equals(key.toUpperCase(java.util.Locale.ROOT)) && !key.startsWith("_");
    }

    @Override
    public String toString() {
        return code;
    }
}
