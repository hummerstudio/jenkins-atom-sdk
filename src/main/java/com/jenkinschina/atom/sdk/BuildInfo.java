package com.jenkinschina.atom.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The build an atom runs in, as the runner hands it over ({@code x-jenkins.context: ["build"]}).
 *
 * <p>This is read-only data — the atom sees the number, the job, the parameters it was started with, the causes and
 * the previous build — and never a live Jenkins object. {@link AtomContext#build()} returns it, or {@code null} when
 * the atom did not ask.
 */
public final class BuildInfo {

    private final int number;
    private final String job;
    private final String url;
    private final String displayName;
    private final String description;
    private final String result;
    private final boolean building;
    private final long timestamp;
    private final long durationMs;
    private final Map<String, String> parameters;
    private final List<String> causes;
    private final BuildInfo previous;

    private BuildInfo(
            int number,
            String job,
            String url,
            String displayName,
            String description,
            String result,
            boolean building,
            long timestamp,
            long durationMs,
            Map<String, String> parameters,
            List<String> causes,
            BuildInfo previous) {
        this.number = number;
        this.job = job;
        this.url = url;
        this.displayName = displayName;
        this.description = description;
        this.result = result;
        this.building = building;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.causes = Collections.unmodifiableList(new ArrayList<>(causes));
        this.previous = previous;
    }

    public int getNumber() {
        return number;
    }

    public String getJob() {
        return job;
    }

    public String getUrl() {
        return url;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** {@code SUCCESS}, {@code FAILURE}, … or empty while the build is still running. */
    public String getResult() {
        return result;
    }

    public boolean isBuilding() {
        return building;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** The parameters this build was started with; empty when it was not parameterised. */
    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    /** Human-readable causes, e.g. "Started by user tangming". */
    public List<String> getCauses() {
        return causes;
    }

    /** The build before this one, or {@code null}. */
    public BuildInfo getPrevious() {
        return previous;
    }

    /** Parses the runner's {@code build.json}; missing fields become empty values, not exceptions. */
    static BuildInfo parse(Map<String, Object> json) {
        if (json == null) {
            return null;
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        Object rawParameters = json.get("parameters");
        if (rawParameters instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parameters.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        List<String> causes = new ArrayList<>();
        Object rawCauses = json.get("causes");
        if (rawCauses instanceof List<?> list) {
            for (Object cause : list) {
                if (cause != null) {
                    causes.add(String.valueOf(cause));
                }
            }
        }
        Object rawPrevious = json.get("previousBuild");
        BuildInfo previous = rawPrevious instanceof Map<?, ?> map ? parse(asMap(map)) : null;

        return new BuildInfo(
                toInt(json.get("number")),
                text(json.get("job")),
                text(json.get("url")),
                text(json.get("displayName")),
                text(json.get("description")),
                text(json.get("result")),
                Boolean.parseBoolean(text(json.get("building"))),
                toLong(json.get("timestamp")),
                toLong(json.get("durationMs")),
                parameters,
                causes,
                previous);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int toInt(Object value) {
        try {
            return value == null ? 0 : (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long toLong(Object value) {
        try {
            return value == null ? 0L : (long) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
