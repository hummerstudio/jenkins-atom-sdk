package io.github.hummerstudio.atom.sdk;

import java.util.List;
import java.util.Map;

/**
 * One atom plugin. Implement this interface, register it in
 * {@code META-INF/services/io.github.hummerstudio.atom.sdk.AtomTask}, and point {@code task.json}'s
 * {@code execution.target} at {@code java -cp <your jar> io.github.hummerstudio.atom.sdk.AtomRunner}.
 */
public interface AtomTask {

    AtomResult run(AtomContext context) throws Exception;
}
