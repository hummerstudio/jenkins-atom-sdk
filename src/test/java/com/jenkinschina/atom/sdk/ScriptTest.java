package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Shell.Script}: the semantics an atom relies on when it hands a script to an interpreter. */
class ScriptTest {

    @Test
    void runsTheScriptAndKeepsItsOutput() {
        Shell.Result result = Shell.run(Shell.Script.of("echo hello-from-script"));
        assertEquals(0, result.exitCode());
        assertTrue(result.ok());
        assertTrue(result.output().contains("hello-from-script"), result.output());
    }

    @Test
    void aNonZeroExitIsReturnedNotThrownWhenTheCallerOptsOut() {
        Shell.Result result = Shell.run(Shell.Script.builder()
                .script("echo before-exit; exit 7")
                .failOnError(false)
                .build());
        assertEquals(7, result.exitCode());
        assertFalse(result.ok());
        assertTrue(result.output().contains("before-exit"), result.output());
    }

    @Test
    void failOnErrorTurnsANonZeroExitIntoAFailureThatNamesIt() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Shell.run(Shell.Script.of("exit 3")));
        assertTrue(failure.getMessage().contains("exited with 3"), failure.getMessage());
    }

    @Test
    void theEnvironmentIsScopedToTheScript() {
        Shell.Result result = Shell.run(Shell.Script.builder()
                .script("echo \"value=$SCOPED_VALUE\"")
                .env("SCOPED_VALUE", "42")
                .build());
        assertTrue(result.output().contains("value=42"), result.output());
        assertEquals(null, System.getenv("SCOPED_VALUE"), "the atom's own environment must not be touched");
    }

    /** Quoting and non-ASCII survive because the script goes to a file, not into {@code -c}. */
    @Test
    void quotingAndUnicodeAreNotRewritten() {
        Shell.Result result = Shell.run(Shell.Script.of(
                "echo 'single $not-expanded'\necho \"double $HOME\"\necho '中文与 emoji 🚀'"));
        assertTrue(result.output().contains("single $not-expanded"), result.output());
        assertTrue(result.output().contains("中文与 emoji 🚀"), result.output());
    }

    /** A megabyte of script would blow past the argv limit if it were passed to {@code sh -c}. */
    @Test
    void aLongScriptStillRuns() {
        StringBuilder script = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            script.append("# padding line ").append(i).append('\n');
        }
        script.append("echo long-script-done\n");
        assertTrue(script.length() > 300_000, "the fixture should be well past any argv limit");

        Shell.Result result = Shell.run(Shell.Script.of(script.toString()));
        assertTrue(result.output().contains("long-script-done"), result.output());
    }

    @Test
    void argumentsArePassedThrough() {
        Shell.Result result = Shell.run(Shell.Script.builder()
                .script("echo \"first=$1 second=$2\"")
                .args("alpha", "beta")
                .build());
        assertTrue(result.output().contains("first=alpha second=beta"), result.output());
    }

    @Test
    void theWorkingDirectoryIsRespected(@TempDir Path dir) throws Exception {
        Shell.Result result = Shell.run(Shell.Script.builder()
                .script("pwd && echo marker > from-script.txt")
                .workdir(dir)
                .build());
        assertTrue(result.output().contains(dir.toRealPath().toString()), result.output());
        assertTrue(Files.exists(dir.resolve("from-script.txt")), "the script should have written into the workdir");
    }

    @Test
    void aTimeoutKillsTheScriptAndSaysSo() {
        long started = System.nanoTime();
        Shell.Result result = Shell.run(Shell.Script.builder()
                .script("sleep 30")
                .timeout(Duration.ofMillis(700))
                .failOnError(false)
                .build());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertTrue(result.timedOut(), "a killed script must be reported as a timeout");
        assertEquals(-1, result.exitCode());
        assertTrue(elapsedMs < 20_000, "the timeout should have fired, took " + elapsedMs + " ms");
        assertTrue(result.describe().contains("timed out"), result.describe());
    }

    @Test
    void aMissingInterpreterIsNamedBeforeAnythingRuns() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Shell.run(Shell.Script.builder()
                        .interpreter(Shell.Interpreter.PYTHON)
                        .interpreterPath("/nonexistent/python-does-not-exist")
                        .script("print(1)")
                        .build()));
        assertTrue(failure.getMessage().contains("/nonexistent/python-does-not-exist"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not found"), failure.getMessage());
    }

    @Test
    void pythonRunsWhenItIsInstalled() {
        Assumptions.assumeTrue(Shell.which("python3") != null, "python3 is not installed here");
        Shell.Result result = Shell.run(Shell.Script.builder()
                .interpreter(Shell.Interpreter.PYTHON)
                .script("import os\nprint('py', os.environ.get('WHO', 'nobody'))\n")
                .env("WHO", "atom")
                .build());
        assertTrue(result.output().contains("py atom"), result.output());
    }

    @Test
    void nodeRunsWhenItIsInstalled() {
        Assumptions.assumeTrue(Shell.which("node") != null, "node is not installed here");
        Shell.Result result = Shell.run(Shell.Script.builder()
                .interpreter(Shell.Interpreter.NODE)
                .script("console.log('node', process.version.startsWith('v'))")
                .build());
        assertTrue(result.output().contains("node true"), result.output());
    }

    @Test
    void whichFindsAProgramOnPathAndRejectsNonsense() {
        assertNotNull(Shell.which("sh"));
        assertEquals(null, Shell.which("definitely-not-a-program-xyz"));
        assertEquals(null, Shell.which("/nope/nothing"));
    }

    @Test
    void interpreterNamesAreParsedLeniently() {
        assertEquals(Shell.Interpreter.PYTHON, Shell.Interpreter.of("python3"));
        assertEquals(Shell.Interpreter.PYTHON, Shell.Interpreter.of("Python"));
        assertEquals(Shell.Interpreter.NODE, Shell.Interpreter.of("nodejs"));
        assertEquals(Shell.Interpreter.PWSH, Shell.Interpreter.of("powershell7"));
        assertEquals(Shell.Interpreter.BAT, Shell.Interpreter.of("cmd.exe"));
        assertThrows(IllegalArgumentException.class, () -> Shell.Interpreter.of("brainfuck"));
    }

    @Test
    void aScriptFileOnDiskIsUsedAsIs(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("task.sh");
        Files.writeString(file, "echo from-file\n");
        Shell.Result result = Shell.run(Shell.Script.builder().file(file).build());
        assertTrue(result.output().contains("from-file"), result.output());
    }
}
