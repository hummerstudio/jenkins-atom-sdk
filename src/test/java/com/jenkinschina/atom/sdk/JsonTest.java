package com.jenkinschina.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JsonTest {

    @Test
    public void parsesNestedStructuresAndEscapes() {
        Map<String, Object> parsed = Json.parseObject(
                "{\"a\":\"x\\\"y\\\\z\\n\",\"n\":42,\"d\":1.5,\"b\":true,\"nil\":null,"
                        + "\"list\":[1,\"two\",{\"k\":\"v\"}],\"u\":\"\\u4e2d\"}");

        assertEquals("x\"y\\z\n", parsed.get("a"));
        assertEquals(42L, parsed.get("n"));
        assertEquals(1.5d, parsed.get("d"));
        assertEquals(Boolean.TRUE, parsed.get("b"));
        assertTrue(parsed.containsKey("nil"));
        assertEquals(3, ((List<?>) parsed.get("list")).size());
        assertEquals("中", parsed.get("u"));
    }

    @Test
    public void writesValidJsonThatParsesBack() {
        Map<String, Object> value = Map.of(
                "s", "quote\" backslash\\ newline\n tab\t",
                "n", 7,
                "list", List.of("a", "b"),
                "nested", Map.of("k", true));

        String json = Json.write(value);
        Map<String, Object> back = Json.parseObject(json);

        assertEquals(value.get("s"), back.get("s"));
        assertEquals(7L, back.get("n"));
        assertEquals("a", ((List<?>) back.get("list")).get(0));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) back.get("nested")).get("k"));
    }

    @Test
    public void rejectsTrailingGarbage() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> Json.parse("{\"a\":1} tail"));
    }
}
