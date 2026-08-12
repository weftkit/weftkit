package org.weftkit.wiring.runtime.sample.internal;

import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class Machine {

    private final Engine engine;

    public Machine(Engine engine) {
        this.engine = engine;
    }

    public String signature() {
        return engine.signature() + "+machine";
    }
}
