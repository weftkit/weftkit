package org.weftkit.wiring.processor.validate.element;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;
import org.weftkit.wiring.processor.model.Product;

// A @Provides getter delivers a value the loader cannot build itself, so it must be readable
// once its singleton owner has loaded, at startup for an eager owner and at first
// materialization for a lazy one
public final class ProvidesRule extends ElementRule<ExecutableElement> {

    public ProvidesRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
    }

    @Override
    public void validate(ExecutableElement getter) {
        TypeElement owner = (TypeElement) getter.getEnclosingElement();
        String product = mirrors.erased(getter.getReturnType());
        boolean valid = diagnostics
                .check(getter)
                .require(
                        getter.getModifiers().contains(Modifier.PUBLIC)
                                && getter.getParameters().isEmpty()
                                && getter.getReturnType().getKind() != TypeKind.VOID,
                        "@Provides requires a public no-argument getter")
                .require(
                        owner.getAnnotation(Wired.class) != null && mirrors.isSingleton(owner),
                        "@Provides getters must live on a @Wired singleton")
                .require(
                        mirrors.isReferencable(getter.getReturnType())
                                || mirrors.isAccessibleFrom(getter.getReturnType(), mirrors.packageName(owner)),
                        "Product type must be public or live in the owner package: " + product)
                .passed();
        if (!valid) return;
        Product previous = collector.addProduct(getter, owner);
        if (previous != null) diagnostics.error(getter, "Product is already provided by " + previous.owner());
    }
}
