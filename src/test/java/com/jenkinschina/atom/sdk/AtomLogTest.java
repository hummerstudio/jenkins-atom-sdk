package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Every line the SDK writes says which atom wrote it: {@code [atom:<code>:<version>]}. */
class AtomLogTest {

    @AfterEach
    void clear() {
        System.clearProperty("atom.code");
        System.clearProperty("atom.version");
    }

    @Test
    void thePrefixCarriesTheCodeAndTheVersion() {
        System.setProperty("atom.code", "git-checkout");
        System.setProperty("atom.version", "1.0.0");
        assertEquals("[atom:git-checkout:1.0.0]", AtomLog.prefix());
    }

    @Test
    void aCodeWithoutAVersionStillReads() {
        System.setProperty("atom.code", "git-checkout");
        assertEquals("[atom:git-checkout]", AtomLog.prefix());
    }

    @Test
    void withoutTheRunnerTheLineKeepsTheOldShape() {
        // a jar started by hand has no ATOM_CODE: [atom::] would be noise, and a guess would be a lie
        assertEquals("[atom]", AtomLog.prefix());
    }

    @Test
    void aLineIsThePrefixAndTheMessage() {
        System.setProperty("atom.code", "git-checkout");
        System.setProperty("atom.version", "1.0.0");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomLog.println(new PrintStream(bytes, true, StandardCharsets.UTF_8), "status=success");
        String line = bytes.toString(StandardCharsets.UTF_8).strip();
        assertEquals("[atom:git-checkout:1.0.0] status=success", line);
        assertTrue(line.indexOf('\n') < 0, "one line per call");
    }

    @Test
    void theRunningAtomIsNamedInEveryLineItWrites() {
        // the runner tags its own lines this way, and so does every context.log() an atom makes
        System.setProperty("atom.code", "demo");
        System.setProperty("atom.version", "0.2.0");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new AtomContext(java.util.Map.of(), java.util.Map.of(), java.nio.file.Paths.get("."),
                java.nio.file.Paths.get("."), out)
                .log("hello");
        assertEquals("[atom:demo:0.2.0] hello", bytes.toString(StandardCharsets.UTF_8).strip());
    }
}
