package org.weftkit.wiring.processor.fixture;

import java.util.List;
import org.weftkit.wiring.processor.spi.ModuleContributor;

/**
 * Contributes module classes that only some test compilations provide, registered by hand in
 * META-INF/services. Plain names on purpose: the classes exist as sources of individual tests,
 * never as class literals here.
 */
public final class FixtureContributor implements ModuleContributor {

    @Override
    public List<String> modules() {
        return List.of("test.Contributed", "test.ContributedClash");
    }
}
