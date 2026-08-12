package org.weftkit.wiring.processor.model;

public record Parameter(
        String erasedClass,
        String declaredType,
        String qualifier,
        boolean singleton,
        boolean internal,
        boolean optional,
        int index) {}
