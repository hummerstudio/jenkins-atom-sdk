package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs an atom exactly the way the Jenkins runner does: files in, files out. */
public class AtomRunnerTest {

    @TempDir
    Path tmp;

    private Path dir(String name) throws java.io.IOException {
        return java.nio.file.Files.createDirectories(tmp.resolve(name));
    }

    @BeforeEach
    public void selectEchoByDefault() {
        // the test jar ships two implementations; the runner is told which atom is being executed
        System.setProperty("atom.code", "echo");
    }

    @AfterEach
    public void clearProperty() {
        System.clearProperty("atom.task.class");
        System.clearProperty("atom.code");
    }

    @Test
    public void writesTheBkCiOutputContract() throws Exception {
        Path dir = dir("run");
        Path ws = dir("ws");
        Files.writeString(dir.resolve("input.json"),
                "{\"msg\":\"hello\",\"times\":\"2\",\"bkWorkspace\":\"" + ws.toString().replace("\\", "\\\\") + "\"}",
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(".secrets.json"), "{\"token\":\"s3cret-value\"}", StandardCharsets.UTF_8);

        ByteArrayOutputStream console = new ByteArrayOutputStream();
        int code = AtomRunner.run(dir, "input.json", "output.json", new PrintStream(console, true, "UTF-8"));

        assertEquals(0, code, console.toString("UTF-8"));
        assertFalse(Files.exists(dir.resolve(".secrets.json")), "secrets file must be removed");
        assertTrue(console.toString("UTF-8").contains("msg=hello times=2 secretPresent=true"));

        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        assertEquals("success", output.get("status"));
        assertEquals("done", output.get("message"));

        Map<?, ?> data = (Map<?, ?>) output.get("data");
        assertEquals("hello", ((Map<?, ?>) data.get("echo")).get("value"));
        Map<?, ?> token = (Map<?, ?>) data.get("tokenOut");
        assertEquals("s3cret-value", token.get("value"));
        assertEquals(Boolean.TRUE, token.get("isSensitive"));
        assertEquals("artifact", ((Map<?, ?>) data.get("files")).get("type"));
        assertEquals("report", ((Map<?, ?>) data.get("report")).get("type"));
        assertEquals("index.html", ((Map<?, ?>) data.get("report")).get("target"));
    }

    @Test
    public void turnsAnExceptionIntoAnErrorResult() throws Exception {
        System.setProperty("atom.task.class", BoomTask.class.getName());
        Path dir = dir("boom");
        Files.writeString(dir.resolve("input.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(".secrets.json"), "{\"token\":\"x\"}", StandardCharsets.UTF_8);

        int code = AtomRunner.run(dir, "input.json", "output.json",
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));

        assertEquals(1, code);
        assertFalse(Files.exists(dir.resolve(".secrets.json")));
        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        assertEquals("error", output.get("status"));
        assertEquals(3L, output.get("errorType"));
        assertTrue(String.valueOf(output.get("message")).contains("boom"));
    }

    @Test
    public void runsWithoutInputOrSecrets() throws Exception {
        Path dir = dir("bare");
        int code = AtomRunner.run(dir, "input.json", "output.json",
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));
        assertEquals(0, code);
        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        assertEquals("success", output.get("status"));
    }

    @Test
    public void picksTheTaskThatDeclaresTheAtomCode() throws Exception {
        System.clearProperty("atom.code");
        System.setProperty("atom.code", "boom");
        Path dir = tmp.resolve("pick");
        Files.createDirectories(dir);

        int code = AtomRunner.run(dir, "input.json", "output.json",
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));

        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        assertTrue(String.valueOf(output.get("message")).contains("boom"), output.toString());
        assertEquals(1, code);
    }

    @Test
    public void anAmbiguousJarFailsInsteadOfGuessing() throws Exception {
        System.clearProperty("atom.code");
        Path dir = tmp.resolve("ambiguous");
        Files.createDirectories(dir);
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        int code = AtomRunner.run(dir, "input.json", "output.json",
                new PrintStream(console, true, "UTF-8"));

        assertEquals(1, code);
        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        String message = String.valueOf(output.get("message"));
        assertTrue(message.contains("2 AtomTask implementations"), message);
        assertTrue(message.contains("EchoTask"), message);
        assertTrue(message.contains("BoomTask"), message);
    }

    @Test
    public void anExplicitClassWinsOverTheAtomCode() throws Exception {
        System.setProperty("atom.task.class", BoomTask.class.getName());
        Path dir = tmp.resolve("explicit");
        Files.createDirectories(dir);

        AtomRunner.run(dir, "input.json", "output.json",
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));

        Map<String, Object> output = Json.parseObject(Files.readString(dir.resolve("output.json")));
        assertTrue(String.valueOf(output.get("message")).contains("boom"));
    }

    @Test
    public void resultSerialisesListsAndOmittedFields() {
        AtomResult result = AtomResult.failure("nope").artifact("a", List.of("x", "y"));
        Map<String, Object> json = result.toJson();
        assertEquals("failure", json.get("status"));
        assertFalse(json.containsKey("errorType"));
        assertFalse(json.containsKey("errorCode"));
        assertEquals(List.of("x", "y"), ((Map<?, ?>) ((Map<?, ?>) json.get("data")).get("a")).get("value"));
    }
}
