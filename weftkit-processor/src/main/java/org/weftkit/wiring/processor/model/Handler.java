package org.weftkit.wiring.processor.model;

/** The module handling an annotation type, by qualified name for the model and binary name for the runtime. */
public record Handler(String module, String binaryName) {}
