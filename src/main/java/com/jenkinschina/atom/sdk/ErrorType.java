package com.jenkinschina.atom.sdk;

/**
 * Error classification, written to {@code output.json.errorType}.
 *
 * <p>The numeric codes follow the BK-CI convention (1 user, 2 third-party, 3 plugin, 4 system) so dashboards that
 * already bucket BK-CI failures keep working after a port. 5 is ours: a step that could not run because the agent
 * lacks something — no docker daemon, no node on PATH, a tool that is not installed. That is not the user's mistake
 * and not the third party's, and it is the most common failure on a mixed fleet, so it deserves its own bucket.
 */
public enum ErrorType {
    USER(1),
    THIRD_PART(2),
    PLUGIN(3),
    SYSTEM(4),

    /** The agent could not satisfy the step: missing tool, missing daemon, unreadable device. */
    ENVIRONMENT(5);

    private final int code;

    ErrorType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
