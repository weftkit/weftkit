package org.weftkit.wiring.processor.validate.element;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;
import org.weftkit.wiring.processor.spi.AnnotationMirrors;

// Checks the recorded @HandledBy annotations of source and module components. Runs after module resolution
public final class HandledByRule {

    private static final String WEFT_MODULE = WeftModule.class.getCanonicalName();

    private final WiringModel model;

    private final Mirrors mirrors;

    private final Elements elements;

    private final Diagnostics diagnostics;

    private final NamedTypes namedTypes = new NamedTypes();

    public HandledByRule(WiringModel model, Mirrors mirrors, Elements elements, Diagnostics diagnostics) {
        this.model = model;
        this.mirrors = mirrors;
        this.elements = elements;
        this.diagnostics = diagnostics;
    }

    /** Checks every handled annotation, given the disabled modules by class name, each mapped to its id. */
    public void check(Map<String, String> disabledModules) {
        model.handledAnnotations().forEach((component, annotations) -> {
            TypeElement element = mirrors.typeElement(component);
            for (AnnotationMirror annotation : annotations) {
                TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
                String carries = "Component " + component + " carries @" + type.getSimpleName();
                String typeName = type.getQualifiedName().toString();
                String declared = model.handlers().get(typeName).module();
                if (!isModule(declared))
                    diagnostics.error(element, carries + ", handled by " + declared + ", which is not a @WeftModule");
                String module = model.handler(typeName).module();
                if (disabledModules.containsKey(module))
                    diagnostics.error(
                            element,
                            carries + ", handled by module " + disabledModules.get(module)
                                    + ", which @Registry disables");
                for (String hidden : hiddenTypes(annotation))
                    diagnostics.error(element, carries + " naming " + hidden + ", which must be public");
            }
        });
    }

    private boolean isModule(String name) {
        TypeElement module = mirrors.typeElement(name);
        return module != null && AnnotationMirrors.annotation(module, WEFT_MODULE) != null;
    }

    private Set<String> hiddenTypes(AnnotationMirror annotation) {
        Set<TypeMirror> named = new HashSet<>();
        namedTypes.visitAnnotation(annotation, named);
        Set<String> hidden = new TreeSet<>();
        for (TypeMirror type : named) if (!mirrors.isReferencable(type)) hidden.add(mirrors.erased(type));
        return hidden;
    }

    private final class NamedTypes extends SimpleAnnotationValueVisitor14<Void, Set<TypeMirror>> {

        @Override
        public Void visitAnnotation(AnnotationMirror annotation, Set<TypeMirror> named) {
            named.add(annotation.getAnnotationType());
            for (ExecutableElement member : ElementFilter.methodsIn(
                    annotation.getAnnotationType().asElement().getEnclosedElements()))
                addType(member.getReturnType(), named);
            for (AnnotationValue value :
                    elements.getElementValuesWithDefaults(annotation).values()) value.accept(this, named);
            return null;
        }

        @Override
        public Void visitType(TypeMirror type, Set<TypeMirror> named) {
            addType(type, named);
            return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement constant, Set<TypeMirror> named) {
            named.add(constant.asType());
            return null;
        }

        private static void addType(TypeMirror type, Set<TypeMirror> named) {
            while (type instanceof ArrayType array) type = array.getComponentType();
            if (type instanceof WildcardType wildcard) {
                TypeMirror bound =
                        wildcard.getExtendsBound() != null ? wildcard.getExtendsBound() : wildcard.getSuperBound();
                if (bound != null) addType(bound, named);
                return;
            }
            if (!(type instanceof DeclaredType declared)) return;
            named.add(declared);
            for (TypeMirror argument : declared.getTypeArguments()) addType(argument, named);
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> values, Set<TypeMirror> named) {
            for (AnnotationValue value : values) value.accept(this, named);
            return null;
        }
    }
}
