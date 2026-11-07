package org.weftkit.wiring.folia;

import org.weftkit.wiring.WeftModule;

/**
 * Woven into every registry compiled with weftkit-folia on the classpath. Ships
 * {@link SchedulerDetection}. Disable with {@code @Registry(disable = FoliaModules.SCHEDULER)}.
 */
@WeftModule(id = FoliaModules.SCHEDULER, components = SchedulerDetection.class)
public final class FoliaModule {

    public FoliaModule() {}
}
