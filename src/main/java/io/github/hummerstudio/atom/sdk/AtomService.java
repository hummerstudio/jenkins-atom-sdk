package io.github.hummerstudio.atom.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the atom code and the parameter class an {@link AtomTask} expects, mirroring BK-CI's
 * {@code @AtomService(paramClass = ...)} so a ported plugin keeps its shape.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtomService {

    /** Atom code, defaults to {@code task.json}'s {@code atomCode}. */
    String value() default "";

    /** Parameter class bound from {@code input.json} by {@link AtomContext#getParam(Class)}. */
    Class<?> paramClass() default Object.class;
}
