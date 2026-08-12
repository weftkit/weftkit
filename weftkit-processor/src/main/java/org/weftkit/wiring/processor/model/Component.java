package org.weftkit.wiring.processor.model;

import java.util.List;

public record Component(boolean singleton, boolean lazy, String qualifier, List<Parameter> parameters) {}
