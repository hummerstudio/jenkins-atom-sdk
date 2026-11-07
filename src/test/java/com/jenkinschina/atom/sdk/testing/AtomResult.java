package com.jenkinschina.atom.sdk.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * What one run of an atom produced: the exit code, the console, the workspace, and {@code output.json}.
 *
 * <p>Assertions throw {@link AssertionError} with the console and the output document attached, so a failure says what
 * the atom did instead of only which line of the test failed.
 */
public final class AtomResult {

    private final String code;
    private final Path work;
    private final int exit;
    private final String console;
    private Map<String, Object> document;

    AtomResult(String code, Path work, int exit, String console) {
        this.code = code;
        this.work = work;
        this.exit = exit;
        this.console = console;
    }

    public int exit() {
        return exit;
    }

    public String console() {
        return console;
    }

    public Path work() {
        return work;
    }

    /** {@code output.json}, parsed; empty when the atom refused before writing one. */
    public Map<String, Object> document() {
        if (document == null) {
            Path file = work.resolve("output.json");
            document = Files.isRegularFile(file) ? Json.parseObject(read(file)) : Map.of();
        }
        return document;
    }

    /** A field of {@code output.json}, with the {@code data} wrapper unwrapped — {@code field("created")} is enough. */
    public String field(String path) {
        Object node = document().get("data") instanceof Map<?, ?> data && data.containsKey(first(path))
                ? data
                : document();
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return "";
            }
            node = map.get(part);
        }
        if (node instanceof Map<?, ?> map && map.containsKey("value")) {
            node = map.get("value");
        }
        return node == null ? "" : String.valueOf(node);
    }

    /** {@code status} of the run: success, failure, skipped… */
    public String status() {
        Object status = document().get("status");
        return status == null ? "" : String.valueOf(status);
    }

    /** The bytes the atom wrote, or {@code null} when it wrote nothing. */
    public byte[] bytes(String path) {
        Path file = work.resolve(path);
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            throw new AssertionError("cannot read " + path, e);
        }
    }

    /** The text the atom wrote, or {@code null} when the file is not there. */
    public String file(String path) {
        byte[] bytes = bytes(path);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public boolean exists(String path) {
        return Files.exists(work.resolve(path));
    }

    public AtomResult assertOk() {
        assertEquals(0, exit, code + " should succeed\nconsole:\n" + console + "\noutput:\n" + pretty());
        return this;
    }

    /** The atom refused: a non-zero exit, with the reason in {@code output.json}. */
    public AtomResult assertRefused() {
        assertNotEquals(0, exit, code + " should refuse\nconsole:\n" + console + "\noutput:\n" + pretty());
        assertTrue(document().containsKey("error") || "failure".equals(status()) || "error".equals(status()),
                code + " refused without saying why\noutput:\n" + pretty());
        return this;
    }

    @Override
    public String toString() {
        return code + " exit=" + exit + "\n" + pretty();
    }

    private String pretty() {
        return Json.write(document());
    }

    private static String first(String path) {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + file, e);
        }
    }
}
