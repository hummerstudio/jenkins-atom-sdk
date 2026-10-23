package com.jenkinschina.atom.sdk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Run external commands from an atom.
 *
 * <p>Output goes straight to the atom's stdout/stderr, so the runner captures it in the build log and applies the same
 * redaction as everything else. Exit codes are returned, never swallowed: {@link #check(int)} turns a non-zero code
 * into a failure with the command in the message.
 *
 * <pre>{@code
 * Shell.sh("tar -czf out.tgz dist");          // /bin/sh -c
 * Shell.bat("dir");                            // cmd.exe /c
 * Shell.powershell("Get-ChildItem");           // powershell -Command
 * Shell.check(Shell.sh("test -d dist"));       // fail the atom when it returns non-zero
 * String out = Shell.capture("git", "rev-parse", "HEAD");
 * }</pre>
 */
public final class Shell {

    private Shell() {
    }

    /** {@code /bin/sh -c script}. */
    public static int sh(String script) {
        return run("/bin/sh", "-c", script);
    }

    /** {@code bash -c script}, for scripts that need bashisms. */
    public static int bash(String script) {
        return run("bash", "-c", script);
    }

    /** {@code cmd.exe /c script}. */
    public static int bat(String script) {
        return run("cmd.exe", "/c", script);
    }

    /** {@code powershell -NoProfile -NonInteractive -Command script}. */
    public static int powershell(String script) {
        return run("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }

    /** Run a command in the current working directory, streaming its output to the build log. */
    public static int run(String... command) {
        return run(null, command);
    }

    /** Run a command in {@code workingDir} (null = the atom's working directory), streaming output. */
    public static int run(Path workingDir, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).inheritIO();
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            return builder.start().waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("cannot run " + String.join(" ", command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running " + String.join(" ", command), e);
        }
    }

    /** Run a command and return its merged stdout/stderr instead of streaming it. */
    public static String capture(String... command) {
        return capture(null, command);
    }

    /** Run a command in {@code workingDir} and return its merged stdout/stderr. */
    public static String capture(Path workingDir, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("command " + String.join(" ", command) + " exited with " + code
                        + (output.isBlank() ? "" : ": " + output.strip()));
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("cannot run " + String.join(" ", command) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running " + String.join(" ", command), e);
        }
    }

    /** Fail the atom when a command did not succeed, quoting the command for the log. */
    public static void check(int exitCode, String... command) {
        if (exitCode != 0) {
            List<String> parts = new ArrayList<>(List.of(command));
            throw new IllegalStateException("command failed with exit code " + exitCode + ": " + String.join(" ", parts));
        }
    }
}
