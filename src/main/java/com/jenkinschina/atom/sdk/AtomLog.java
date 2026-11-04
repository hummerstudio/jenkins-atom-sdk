package com.jenkinschina.atom.sdk;

import java.io.PrintStream;

/**
 * The prefix every line the SDK writes carries: {@code [atom:<code>:<version>]}, e.g. {@code [atom:git-checkout:1.0.0]}.
 *
 * <p>A console that runs several atoms is otherwise a wall of identical {@code [atom]} lines: the reader cannot tell
 * which atom said what, and neither can a script that greps the log. The code and the version come from the runner
 * ({@code ATOM_CODE} / {@code atom_version}), or from {@code -Datom.code=… -Datom.version=…}.
 *
 * <p>When the runner did not say (a jar started by hand), the unknown part is left out and the line reads
 * {@code [atom] message} — the old form. Printing {@code [atom::]} would be worse than saying nothing.
 */
public final class AtomLog {

    private AtomLog() {}

    /** {@code [atom:git-checkout:1.0.0]}, or as much of it as is known. */
    public static String prefix() {
        String code = first("atom.code", "ATOM_CODE", "BK_CI_ATOM_CODE");
        String version = first("atom.version", "atom_version", "ATOM_VERSION", "BK_CI_ATOM_VERSION");
        StringBuilder prefix = new StringBuilder("[atom");
        if (code != null) {
            prefix.append(':').append(code);
            if (version != null) {
                prefix.append(':').append(version);
            }
        }
        return prefix.append(']').toString();
    }

    /** One line of atom output, tagged with the atom that wrote it. */
    public static void println(PrintStream out, String message) {
        out.println(prefix() + " " + message);
    }

    private static String first(String property, String... envNames) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) {
            return value.strip();
        }
        for (String envName : envNames) {
            String fromEnv = System.getenv(envName);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.strip();
            }
        }
        return null;
    }
}
