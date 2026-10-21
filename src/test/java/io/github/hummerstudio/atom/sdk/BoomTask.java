package io.github.hummerstudio.atom.sdk;

public class BoomTask implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) {
        throw new IllegalStateException("boom");
    }
}
