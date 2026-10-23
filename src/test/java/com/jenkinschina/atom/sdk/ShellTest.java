package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class ShellTest {

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void shReturnsTheExitCode() {
        assertEquals(0, Shell.sh("true"));
        assertEquals(3, Shell.sh("exit 3"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void captureReturnsOutputAndThrowsOnFailure() {
        assertTrue(Shell.capture("sh", "-c", "echo hello").strip().equals("hello"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Shell.capture("sh", "-c", "echo boom; exit 7"));
        assertTrue(e.getMessage().contains("exited with 7"), e.getMessage());
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void runsInTheGivenDirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("note.txt"), "x");
        assertEquals("note.txt", Shell.capture(dir, "ls").strip());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void checkFailsWithTheCommandInTheMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Shell.check(2, "make", "dist"));
        assertTrue(e.getMessage().contains("make dist"), e.getMessage());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void batReturnsTheExitCode() {
        assertEquals(0, Shell.bat("echo hi"));
        assertEquals(4, Shell.bat("exit /b 4"));
    }
}
