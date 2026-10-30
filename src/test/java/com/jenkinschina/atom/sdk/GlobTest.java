package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobTest {

    @Test
    void doubleStarMatchesZeroDirectories(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("src/nested"));
        Files.writeString(workspace.resolve("src/top.py"), "a");
        Files.writeString(workspace.resolve("src/nested/deep.py"), "b");
        Files.writeString(workspace.resolve("src/readme.txt"), "c");

        List<Path> found = Glob.find(workspace, "src/**/*.py");
        assertEquals(2, found.size(), "src/**/*.py must find src/top.py as well as src/nested/deep.py");

        assertTrue(Glob.matches("src/**/*.py", Path.of("src/top.py")));
        assertTrue(Glob.matches("src/**/*.py", Path.of("src/a/b/deep.py")));
        assertFalse(Glob.matches("src/**/*.py", Path.of("src/readme.txt")));
    }

    @Test
    void leadingDoubleStarFindsFilesAtTheRoot(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("TEST-root.xml"), "a");
        Files.createDirectories(workspace.resolve("target/surefire-reports"));
        Files.writeString(workspace.resolve("target/surefire-reports/TEST-one.xml"), "b");

        List<Path> found = Glob.find(workspace, "**/TEST-*.xml");
        assertEquals(2, found.size());
        assertTrue(Glob.matches("**/TEST-*.xml", Path.of("TEST-root.xml")));
    }

    @Test
    void aBareNameMatchesAtAnyDepth(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("a/b"));
        Files.writeString(workspace.resolve("a/b/report.json"), "{}");

        assertEquals(1, Glob.find(workspace, "report.json").size());
        assertTrue(Glob.matches("report.json", Path.of("a/b/report.json")));
    }

    @Test
    void commaSeparatedPatternsAreAllUsed(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("a.xml"), "1");
        Files.writeString(workspace.resolve("b.tap"), "2");
        Files.writeString(workspace.resolve("c.txt"), "3");

        assertEquals(2, Glob.find(workspace, "*.xml, *.tap").size());
    }

    @Test
    void variantsCollapseEveryDoubleStar() {
        Set<String> variants = Glob.variants("a/**/b/**/c");
        assertTrue(variants.contains("a/**/b/**/c"));
        assertTrue(variants.contains("a/b/**/c"));
        assertTrue(variants.contains("a/**/b/c"));
        assertTrue(variants.contains("a/b/c"));
    }

    @Test
    void nothingIsMatchedWithoutAPattern(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("x.txt"), "1");
        assertTrue(Glob.find(workspace, "").isEmpty());
        assertFalse(Glob.matches(null, Path.of("x.txt")));
        assertFalse(Glob.matches("   ", Path.of("x.txt")));
    }

    @Test
    void aMalformedPatternDoesNotKillTheList(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("ok.xml"), "1");
        // "[" alone is not a valid glob; the valid pattern next to it must still work
        assertEquals(1, Glob.find(workspace, "[, *.xml").size());
    }
}
