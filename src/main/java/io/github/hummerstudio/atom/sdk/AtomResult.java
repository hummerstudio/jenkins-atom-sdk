package io.github.hummerstudio.atom.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an atom returns. The runner reads the serialised form ({@code output.json}), which is exactly the BK-CI
 * contract: {@code status} / {@code message} / {@code errorType} / {@code errorCode} / {@code data}.
 */
public final class AtomResult {

    /** {@code success}, {@code failure} or {@code error}. */
    private String status = "success";

    private String message;
    private ErrorType errorType;
    private Integer errorCode;
    private final Map<String, Object> data = new LinkedHashMap<>();

    public static AtomResult success() {
        return new AtomResult();
    }

    public static AtomResult success(String message) {
        AtomResult result = new AtomResult();
        result.message = message;
        return result;
    }

    /** A business failure (the build fails, but nothing crashed). */
    public static AtomResult failure(String message) {
        AtomResult result = new AtomResult();
        result.status = "failure";
        result.message = message;
        return result;
    }

    /** An error with a classification, for dashboards that bucket failures by {@code errorType}. */
    public static AtomResult error(ErrorType type, int errorCode, String message) {
        AtomResult result = new AtomResult();
        result.status = "error";
        result.errorType = type == null ? ErrorType.PLUGIN : type;
        result.errorCode = errorCode;
        result.message = message;
        return result;
    }

    /** A plain string output; also exported as the {@code ATOM_<KEY>} environment variable. */
    public AtomResult data(String key, String value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("value", value == null ? "" : value);
        data.put(key, field);
        return this;
    }

    /** A string output that is masked in the build page and logs. */
    public AtomResult sensitiveData(String key, String value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("value", value == null ? "" : value);
        field.put("isSensitive", true);
        data.put(key, field);
        return this;
    }

    /** Files (absolute or workspace-relative) to archive with the build. */
    public AtomResult artifact(String key, List<String> paths) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "artifact");
        field.put("value", new ArrayList<>(paths));
        data.put(key, field);
        return this;
    }

    public AtomResult artifact(String key, String path) {
        return artifact(key, List.of(path));
    }

    /** A report directory inside the workspace; {@code target} is the entry file, e.g. {@code index.html}. */
    public AtomResult report(String key, String label, String path, String target) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "report");
        field.put("label", label);
        field.put("path", path);
        field.put("target", target == null || target.isBlank() ? "index.html" : target);
        data.put(key, field);
        return this;
    }

    /** A report hosted elsewhere; the build page only links to it. */
    public AtomResult reportUrl(String key, String label, String url) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "report");
        field.put("label", label);
        field.put("url", url);
        data.put(key, field);
        return this;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public boolean isSuccess() {
        return "success".equals(status);
    }

    /** The {@code output.json} body. */
    public Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", status);
        if (message != null) {
            root.put("message", message);
        }
        if (errorType != null) {
            root.put("errorType", errorType.getCode());
        }
        if (errorCode != null) {
            root.put("errorCode", errorCode);
        }
        root.put("data", data);
        return root;
    }
}
