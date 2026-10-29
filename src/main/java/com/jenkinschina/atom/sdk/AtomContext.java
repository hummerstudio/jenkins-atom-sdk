package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything an atom may read or write during one run.
 *
 * <p>Nothing here talks to a server: the runner has already materialised {@code input.json} and
 * {@code .secrets.json} in the data directory, so an atom stays a pure function of its files.
 */
public final class AtomContext {

    private final Map<String, Object> params;
    private final Map<String, String> secrets;
    private final Path dataDir;
    private final Path workspace;
    private final PrintStream out;

    AtomContext(
            Map<String, Object> params,
            Map<String, String> secrets,
            Path dataDir,
            Path workspace,
            PrintStream out) {
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.secrets = Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
        this.dataDir = dataDir;
        this.workspace = workspace;
        this.out = out == null ? System.out : out;
    }

    /** Raw value of one input, or {@code null}. */
    public String getParam(String name) {
        Object value = params.get(name);
        return value == null ? null : String.valueOf(value);
    }

    public String getParam(String name, String defaultValue) {
        String value = getParam(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public int getIntParam(String name, int defaultValue) {
        String value = getParam(name);
        try {
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanParam(String name, boolean defaultValue) {
        String value = getParam(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /** Binds every declared field of {@code type} by name; see {@link Params}. */
    public <T> T getParam(Class<T> type) {
        return Params.bind(params, type);
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /** A secret by the name declared in {@code x-jenkins.secrets} (or by credentials id). */
    public String getSecret(String name) {
        return secrets.get(name);
    }

    /** Alias for {@link #getSecret(String)} — native atoms address credentials by id. */
    public String getCredential(String id) {
        return secrets.get(id);
    }

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public String getAtomCode() {
        return getParam("ATOM_CODE", "atom");
    }

    public String getBuildId() {
        return getParam("ATOM_CODE", "0");
    }

    public void log(String message) {
        out.println("[atom] " + message);
    }

    /** Groups related log lines in the Jenkins console (collapsed until expanded). */
    public void group(String name) {
        out.println("[atom] === " + name + " ===");
    }

    // ---------------------------------------------------------------- Jenkins build data

    /** Param name the runner uses for the build log it hands over ({@code x-jenkins.context: ["log"]}). */
    public static final String BUILD_LOG_PARAM = "atom_build_log";

    /** Param name the runner uses for the build metadata file ({@code x-jenkins.context: ["build"]}). */
    public static final String BUILD_JSON_PARAM = "atom_build_json";

    /** True when the task declared {@code x-jenkins.context: ["log"]}; reading the log is a privilege, not a default. */
    public boolean hasBuildLog() {
        String name = getParam(BUILD_LOG_PARAM, System.getenv().getOrDefault(BUILD_LOG_PARAM, ""));
        return !name.isBlank() && java.nio.file.Files.isRegularFile(dataDir.resolve(name));
    }

    /**
     * The build log as far as it has been written — the last two megabytes — or an empty string when the atom did not
     * declare it. The file name belongs to the runner, which is why atoms ask instead of opening files.
     */
    public String buildLog() {
        String name = getParam(BUILD_LOG_PARAM, System.getenv().getOrDefault(BUILD_LOG_PARAM, ""));
        if (name.isBlank()) {
            return "";
        }
        Path file = dataDir.resolve(name);
        try {
            return java.nio.file.Files.isRegularFile(file)
                    ? java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)
                    : "";
        } catch (IOException e) {
            log("cannot read the build log: " + e.getMessage());
            return "";
        }
    }

    /**
     * The build this atom runs in, or {@code null} when it did not declare {@code x-jenkins.context: ["build"]}.
     * Read-only: an atom may look, and change the name/description through {@link #mutate(Mutation)}.
     */
    public BuildInfo build() {
        String name = getParam(BUILD_JSON_PARAM, System.getenv().getOrDefault(BUILD_JSON_PARAM, ""));
        if (name.isBlank()) {
            return null;
        }
        Path file = dataDir.resolve(name);
        if (!java.nio.file.Files.isRegularFile(file)) {
            return null;
        }
        try {
            return BuildInfo.parse(Json.parseObject(
                    java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            log("cannot read the build metadata: " + e.getMessage());
            return null;
        }
    }

    /**
     * Asks the runner to rename the build and/or set its description. The request is written next to the outputs and
     * applied after the atom exits; the runner prints what it changed.
     */
    public void mutate(Mutation mutation) throws IOException {
        if (mutation == null || mutation.isEmpty()) {
            return;
        }
        mutation.writeTo(dataDir);
    }

    /** Shorthand for {@code mutate(Mutation.displayName(name))}. */
    public void setBuildName(String displayName) throws IOException {
        mutate(Mutation.displayName(displayName));
    }

    /** Shorthand for {@code mutate(Mutation.description(text))}. */
    public void setBuildDescription(String description) throws IOException {
        mutate(Mutation.description(description));
    }
}
