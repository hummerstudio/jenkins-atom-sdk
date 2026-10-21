package io.github.hummerstudio.atom.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ParamsTest {

    /** Camel case fields, bound by exact name. */
    public static class Camel {
        public String pullType;
        public int retries;
        public boolean dryRun;
        public List<String> tags;
        public Path report;
    }

    /** Snake case keys from a bk-ci form, bound through the snake_case fallback. */
    public static class Snake {
        public String imageName;
        public Integer port;
    }

    public static class Nested {
        public String name;
        public Inner inner;
    }

    public static class Inner {
        public String value;
    }

    @Test
    public void bindsCamelCaseFieldsAndTypes() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pullType", "TAG");
        params.put("retries", "3");
        params.put("dryRun", "true");
        params.put("tags", List.of("a", "b"));
        params.put("report", "/tmp/report");

        Camel bound = Params.bind(params, Camel.class);

        assertEquals("TAG", bound.pullType);
        assertEquals(3, bound.retries);
        assertTrue(bound.dryRun);
        assertEquals(List.of("a", "b"), bound.tags);
        assertEquals(Path.of("/tmp/report"), bound.report);
    }

    @Test
    public void bindsSnakeCaseKeysAndNestedObjects() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("image_name", "nginx:1.25");
        params.put("port", 8080L);
        Snake snake = Params.bind(params, Snake.class);
        assertEquals("nginx:1.25", snake.imageName);
        assertEquals(Integer.valueOf(8080), snake.port);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("name", "x");
        outer.put("inner", Map.of("value", "y"));
        Nested nested = Params.bind(outer, Nested.class);
        assertEquals("x", nested.name);
        assertEquals("y", nested.inner.value);
    }

    @Test
    public void leavesAbsentFieldsAlone() {
        Camel bound = Params.bind(new LinkedHashMap<>(), Camel.class);
        assertNull(bound.pullType);
        assertEquals(0, bound.retries);
    }
}
