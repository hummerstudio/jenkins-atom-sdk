package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ant-style globs over a workspace, in one place.
 *
 * <p>{@code PathMatcher} reads {@code **&#47;} as "at least one directory", while every tool that ships globs
 * (Ant, Maven, Jenkins' own {@code hasItems}) reads it as "zero or more". So {@code src/**&#47;*.py} finds
 * {@code src/calc.py} there and not here — an atom that quietly matched nothing then reported success.
 *
 * <p>This class fills that gap once instead of in every atom:
 * <pre>{@code
 * List<Path> reports = Glob.find(workspace, "**&#47;surefire-reports/TEST-*.xml");
 * boolean clean = Glob.matches("src/**&#47;*.py", path);
 * }</pre>
 */
public final class Glob {

    /** Files a walk will look at before it gives up: a runaway pattern should not hang a build. */
    public static final int MAX_FILES = 20000;

    /**
     * The runner keeps the atom's own package and its json files here, inside the workspace. A pattern like
     * {@code **&#47;*.jar} would otherwise match the atom's own jar and publish that.
     */
    public static final String RUNNER_DIR = ".atoms";

    private Glob() {}

    /** Whether a workspace-relative path matches any of the comma-separated patterns. */
    public static boolean matches(String patterns, Path relative) {
        if (patterns == null || patterns.isBlank() || relative == null) {
            return false;
        }
        String normalized = relative.toString().replace('\\', '/');
        for (PathMatcher matcher : matchers(patterns)) {
            Path candidate = Path.of(normalized);
            if (matcher.matches(candidate) || matcher.matches(candidate.getFileName())) {
                return true;
            }
            // a pattern without a directory part is meant to match at any depth
            if (!normalized.contains("/") && matcher.matches(candidate.getFileName())) {
                return true;
            }
        }
        return false;
    }

    /** Every regular file under {@code workspace} whose relative path matches, sorted, capped at {@link #MAX_FILES}. */
    public static List<Path> find(Path workspace, String patterns) throws IOException {
        return find(workspace, patterns, false);
    }

    /**
     * @param includeRunnerFiles whether files under {@link #RUNNER_DIR} count as workspace content; only an atom
     *     inspecting the runner's own directory wants that
     */
    public static List<Path> find(Path workspace, String patterns, boolean includeRunnerFiles) throws IOException {
        List<PathMatcher> compiled = matchers(patterns);
        if (compiled.isEmpty()) {
            return List.of();
        }
        List<Path> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspace, 16)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        Path relative = workspace.relativize(path);
                        if (!includeRunnerFiles && relative.getNameCount() > 0
                                && RUNNER_DIR.equals(relative.getName(0).toString())) {
                            return false;
                        }
                        String text = relative.toString().replace('\\', '/');
                        for (PathMatcher matcher : compiled) {
                            if (matcher.matches(Path.of(text)) || matcher.matches(relative.getFileName())) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .sorted()
                    .limit(MAX_FILES)
                    .forEach(found::add);
        }
        return found;
    }

    /**
     * The pattern plus every variant with one {@code **&#47;} segment dropped, so {@code a/**&#47;b/**&#47;c}
     * also tries {@code a/b/**&#47;c}, {@code a/**&#47;b/c} and {@code a/b/c}.
     */
    public static Set<String> variants(String pattern) {
        Set<String> seen = new LinkedHashSet<>();
        if (pattern == null || pattern.isBlank()) {
            return seen;
        }
        String trimmed = pattern.trim();
        seen.add(trimmed);
        Deque<String> queue = new ArrayDeque<>(List.of(trimmed));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int at = current.indexOf("**/");
            while (at >= 0) {
                String collapsed = current.substring(0, at) + current.substring(at + 3);
                if (seen.add(collapsed)) {
                    queue.add(collapsed);
                }
                at = current.indexOf("**/", at + 1);
            }
        }
        return seen;
    }

    private static List<PathMatcher> matchers(String patterns) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String part : split(patterns)) {
            for (String variant : variants(part)) {
                try {
                    matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + variant));
                } catch (IllegalArgumentException ignored) {
                    // a malformed pattern is the caller's problem to report; it must not kill the whole list
                }
            }
        }
        return matchers;
    }

    /** Splits on commas that are not inside quotes, the way task.json inputs usually carry pattern lists. */
    private static List<String> split(String patterns) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : patterns.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        parts.removeIf(part -> part == null || part.isBlank());
        return parts;
    }
}
