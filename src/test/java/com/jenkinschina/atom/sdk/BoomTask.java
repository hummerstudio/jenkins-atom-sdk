package com.jenkinschina.atom.sdk;

@AtomService("boom")
public class BoomTask implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) {
        throw new IllegalStateException("boom");
    }
}
