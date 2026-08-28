package org.weftkit.wiring.processor.fixture;

import org.weftkit.wiring.WeftModule;

/** Precompiled module, woven from the compile classpath like a real module jar. */
@WeftModule(id = "fixture:mod", components = FixtureComponent.class)
public final class FixtureModule {

    public FixtureModule() {}
}
