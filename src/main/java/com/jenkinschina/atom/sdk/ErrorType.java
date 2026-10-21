package com.jenkinschina.atom.sdk;

/**
 * Error classification, written to {@code output.json.errorType}.
 *
 * <p>The numeric codes follow the BK-CI convention (1 user, 2 third-party, 3 plugin, 4 system) so dashboards that
 * already bucket BK-CI failures keep working after a port.
 */
public enum ErrorType {
    USER(1),
    THIRD_PART(2),
    PLUGIN(3),
    SYSTEM(4);

    private final int code;

    ErrorType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
