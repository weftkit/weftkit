package org.weftkit.wiring.runtime;

import java.util.ArrayList;
import java.util.List;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.registry.ComponentRegistry;

/** One activation attempt per loaded graph. All access holds the component graph lock. */
final class ModuleActivation implements CreationListener {
    private enum State {
        READY,
        ACTIVATING,
        ACTIVE,
        DEACTIVATING,
        FINISHED
    }

    private final ComponentRegistry registry;
    private final Components components;
    private final List<ModuleLifecycle> attempted = new ArrayList<>();
    private State state = State.READY;

    ModuleActivation(ComponentRegistry registry, Components components) {
        this.registry = registry;
        this.components = components;
    }

    WeftDiagnostics.ModuleState diagnosticState() {
        return WeftDiagnostics.ModuleState.valueOf(state.name());
    }

    boolean activate() {
        if (state == State.ACTIVE) return true;
        requireIdle();
        if (state == State.FINISHED)
            throw new IllegalStateException("Module activation was already attempted; unload and load before retrying");
        state = State.ACTIVATING;
        try {
            for (Class<?> type : registry.modules()) {
                if (!(components.cached(type) instanceof ModuleLifecycle module)) continue;
                attempted.add(module);
                if (!module.activate()) {
                    deactivateAttempted();
                    return false;
                }
            }
            state = State.ACTIVE;
            return true;
        } catch (RuntimeException | Error failure) {
            if (state == State.ACTIVATING) {
                try {
                    deactivateAttempted();
                } catch (RuntimeException | Error teardown) {
                    if (failure != teardown) failure.addSuppressed(teardown);
                }
            }
            throw failure;
        }
    }

    void deactivate() {
        requireIdle();
        if (state != State.ACTIVE) return;
        deactivateAttempted();
    }

    void requireIdle() {
        if (state == State.ACTIVATING || state == State.DEACTIVATING)
            throw new IllegalStateException("Cannot change graph or module lifecycle during module "
                    + (state == State.ACTIVATING ? "activation" : "deactivation"));
    }

    private void deactivateAttempted() {
        state = State.DEACTIVATING;
        Throwable failure = null;
        try {
            for (int index = attempted.size() - 1; index >= 0; index--) {
                try {
                    attempted.get(index).deactivate();
                } catch (RuntimeException | Error ex) {
                    if (failure == null) failure = ex;
                    else if (failure != ex) failure.addSuppressed(ex);
                }
            }
        } finally {
            attempted.clear();
            state = State.FINISHED;
        }
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }

    @Override
    public void reset() {
        attempted.clear();
        state = State.READY;
    }
}
