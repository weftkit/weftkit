package org.weftkit.wiring.processor.model;

import java.util.List;
import org.weftkit.wiring.registry.Flag;

/** A collected component. A null {@code featureFlag} means unflagged. */
public record Component(
        boolean singleton, boolean lazy, String qualifier, Flag featureFlag, List<Parameter> parameters) {}
