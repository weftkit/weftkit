package org.weftkit.wiring.processor.validate.graph;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.Product;
import org.weftkit.wiring.processor.model.WiringModel;

// A type cannot be created by the loader and delivered by an owner at the same time
final class ProductRule extends GraphRule {

    ProductRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        model.products().forEach((type, qualified) -> {
            if (!model.isComponent(type)) return;
            qualified
                    .values()
                    .forEach(product -> diagnostics.error(
                            getterAnchor(product), "Product type is also a @Wired component: " + type));
        });
    }

    private Element getterAnchor(Product product) {
        TypeElement owner = mirrors.typeElement(product.owner());
        return ElementFilter.methodsIn(owner.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(product.getter()))
                .findFirst()
                .map(Element.class::cast)
                .orElse(owner);
    }
}
