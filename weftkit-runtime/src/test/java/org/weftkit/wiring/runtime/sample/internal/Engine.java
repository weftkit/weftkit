package org.weftkit.wiring.runtime.sample.internal;

import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
final class Engine {

    Engine() {}

    String signature() {
        return "engine";
    }
}
