package org.weftkit.wiring.processor.spi;

import java.util.List;

/**
 * Modules an adapter contributes to every generated registry, discovered through
 * {@link java.util.ServiceLoader} like {@link ComponentRule}. A contributed class absent from the
 * consumer's compile classpath is skipped, so a contribution only applies where its adapter is in
 * use.
 */
public interface ModuleContributor {

    /**
     * Qualified names of the {@code @WeftModule} classes to weave. Derive them from class
     * literals, {@code ListenerModule.class.getName()}, so a rename breaks the adapter build
     * instead of shipping a dead contribution.
     */
    List<String> modules();

    /**
     * Contributed modules that must load before other modules (and unload after them).
     * Dependencies still load first; disabled modules are skipped and replacements retain this order.
     */
    default List<String> loadFirst() {
        return List.of();
    }

    /**
     * Qualified names of ambient types the adapter runtime supplies to every loader, e.g. the
     * plugin instance. Woven into the declared ambient types. A name absent from the compile
     * classpath is skipped like a missing module.
     */
    default List<String> ambientTypes() {
        return List.of();
    }
}
