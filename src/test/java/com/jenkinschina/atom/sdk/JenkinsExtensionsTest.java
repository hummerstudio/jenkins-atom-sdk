package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Jenkins-build side of the SDK: the log and metadata an atom may read, and the one thing it may change.
 *
 * <p>These are contracts with the runner, so the tests assert the file names as much as the values — an atom that
 * stops talking to the runner must fail here, not in a build.
 */
public class JenkinsExtensionsTest {

    @TempDir
    Path tmp;

    private AtomContext context(Map<String, Object> params) {
        return new AtomContext(
                params, Map.of(), tmp, tmp, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private Map<String, Object> params(String... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            params.put(keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    @Test
    public void theBuildLogIsReadThroughTheParamTheRunnerSets() throws Exception {
        Files.writeString(tmp.resolve("build.log"), "line one\nartifact: mylib-2.4.1.jar\n");

        AtomContext context = context(params("atom_build_log", "build.log"));

        assertTrue(context.hasBuildLog());
        assertEquals("line one\nartifact: mylib-2.4.1.jar\n", context.buildLog());
    }

    @Test
    public void withoutTheDeclarationThereIsNoLog() {
        AtomContext context = context(params());

        assertFalse(context.hasBuildLog());
        assertEquals("", context.buildLog());
    }

    @Test
    public void aDeclarationWithoutTheFileIsStillEmptyRatherThanBroken() {
        AtomContext context = context(params("atom_build_log", "build.log"));

        assertFalse(context.hasBuildLog(), "the runner may not have written it yet");
        assertEquals("", context.buildLog());
    }

    @Test
    public void theBuildMetadataIsAvailableWhenDeclared() throws Exception {
        Files.writeString(
                tmp.resolve("build.json"),
                """
                {"number": 12, "job": "parcel", "url": "http://jenkins/job/parcel/12/", "displayName": "#12",
                 "description": "", "building": false, "result": "SUCCESS", "timestamp": 1700000000000,
                 "durationMs": 4200, "parameters": {"channel": "beta"},
                 "causes": ["Started by user tangming"], "previousBuild": {"number": 11, "result": "FAILURE"}}
                """);

        BuildInfo build = context(params("atom_build_json", "build.json")).build();

        assertNotNull(build);
        assertEquals(12, build.getNumber());
        assertEquals("parcel", build.getJob());
        assertEquals("SUCCESS", build.getResult());
        assertEquals("beta", build.getParameter("channel"));
        assertEquals(1, build.getCauses().size());
        assertTrue(build.getCauses().get(0).contains("tangming"));
        assertNotNull(build.getPrevious());
        assertEquals(11, build.getPrevious().getNumber());
        assertEquals("FAILURE", build.getPrevious().getResult());
        assertEquals(4200, build.getDurationMs());
    }

    @Test
    public void withoutTheDeclarationThereIsNoBuildInfo() {
        assertNull(context(params()).build());
    }

    @Test
    public void renamingTheBuildWritesTheRunnersMutationFile() throws Exception {
        AtomContext context = context(params());

        context.setBuildName("release-7");

        String written = Files.readString(tmp.resolve(Mutation.FILE), StandardCharsets.UTF_8);
        assertEquals("{\"displayName\": \"release-7\"}", written);
    }

    @Test
    public void aDescriptionAndANameAreWrittenTogetherAndEscaped() throws Exception {
        AtomContext context = context(params());

        context.mutate(Mutation.of("v2 \"quoted\"", "two\nlines"));

        String written = Files.readString(tmp.resolve(Mutation.FILE), StandardCharsets.UTF_8);
        assertEquals("{\"displayName\": \"v2 \\\"quoted\\\"\", \"description\": \"two\\nlines\"}", written);
    }

    @Test
    public void anEmptyRequestWritesNothing() throws Exception {
        context(params()).mutate(null);

        assertFalse(Files.exists(tmp.resolve(Mutation.FILE)));
    }
}
