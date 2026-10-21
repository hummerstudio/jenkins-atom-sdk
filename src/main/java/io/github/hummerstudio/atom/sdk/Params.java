package io.github.hummerstudio.atom.sdk;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds the flat {@code input.json} map onto a plain Java object, so an atom can declare
 * {@code class DemoParam { public String pullType; }} instead of reading strings by hand.
 *
 * <p>Matching is by field name, then by snake_case of the field name (BK-CI forms often use snake_case keys).
 */
public final class Params {

    private Params() {}

    @SuppressWarnings("unchecked")
    public static <T> T bind(Map<String, Object> params, Class<T> type) {
        if (type == null || type == Object.class) {
            return null;
        }
        if (Map.class.isAssignableFrom(type)) {
            return (T) new LinkedHashMap<>(params);
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!params.containsKey(field.getName()) && !params.containsKey(snake(field.getName()))) {
                    continue;
                }
                Object raw = params.containsKey(field.getName())
                        ? params.get(field.getName())
                        : params.get(snake(field.getName()));
                Object converted = convert(raw, field.getType());
                if (converted == null && field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                field.set(instance, converted);
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "cannot bind " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Object convert(Object raw, Class<?> target) {
        if (raw == null) {
            return null;
        }
        if (target == String.class || target == CharSequence.class) {
            return String.valueOf(raw);
        }
        if (target == int.class || target == Integer.class) {
            return (int) number(raw);
        }
        if (target == long.class || target == Long.class) {
            return (long) number(raw);
        }
        if (target == double.class || target == Double.class) {
            return number(raw);
        }
        if (target == boolean.class || target == Boolean.class) {
            return raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw).trim());
        }
        if (target == Path.class) {
            return Paths.get(String.valueOf(raw));
        }
        if (Collection.class.isAssignableFrom(target)) {
            if (raw instanceof Collection<?> collection) {
                return new ArrayList<>(collection);
            }
            List<Object> single = new ArrayList<>();
            single.add(raw);
            return single;
        }
        if (Map.class.isAssignableFrom(target)) {
            if (raw instanceof Map<?, ?> map) {
                return new LinkedHashMap<>(map);
            }
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((k, v) -> nested.put(String.valueOf(k), v));
            return bind(nested, target);
        }
        return null;
    }

    private static double number(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(raw).trim());
    }

    static String snake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
