package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A change the atom asks the runner to make to the build it runs in.
 *
 * <p>An atom is a separate process and cannot touch Jenkins, so this is a request: the runner reads the file this
 * writes and applies it. Only the two fields below exist — an atom may rename its own build and describe it, nothing
 * else — which is why the contract is a small value object instead of an API handle.
 *
 * <p>Written by {@link AtomContext#mutate(Mutation)}; the file name is the runner's, not the atom's business.
 */
public final class Mutation {

    /** What the runner looks for in the atom's data directory. */
    public static final String FILE = "mutations.json";

    private final String displayName;
    private final String description;

    private Mutation(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static Mutation displayName(String name) {
        return new Mutation(name, null);
    }

    public static Mutation description(String text) {
        return new Mutation(null, text);
    }

    public static Mutation of(String displayName, String description) {
        return new Mutation(displayName, description);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEmpty() {
        return displayName == null && description == null;
    }

    /** Writes the request into the atom's data directory; called by the context, not by atoms. */
    void writeTo(Path dataDir) throws IOException {
        StringBuilder json = new StringBuilder("{");
        if (displayName != null) {
            json.append("\"displayName\": \"").append(escape(displayName)).append('"');
        }
        if (description != null) {
            if (json.length() > 1) {
                json.append(", ");
            }
            json.append("\"description\": \"").append(escape(description)).append('"');
        }
        json.append('}');
        Files.writeString(dataDir.resolve(FILE), json.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
