package com.jenkinschina.atom.sdk;

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
}
