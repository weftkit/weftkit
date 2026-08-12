package org.weftkit.wiring.processor.compiler;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.Qualified;
import org.weftkit.wiring.Singleton;

public final class Mirrors {

    private final ProcessingEnvironment processingEnv;

    public Mirrors(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public String erased(TypeMirror type) {
        return processingEnv.getTypeUtils().erasure(type).toString();
    }

    public Element asElement(TypeMirror type) {
        return processingEnv.getTypeUtils().asElement(type);
    }

    public TypeElement typeElement(String qualifiedName) {
        return processingEnv.getElementUtils().getTypeElement(qualifiedName);
    }

    public String packageName(TypeElement type) {
        return processingEnv
                .getElementUtils()
                .getPackageOf(type)
                .getQualifiedName()
                .toString();
    }

    public boolean assignable(TypeMirror type, TypeMirror target) {
        return processingEnv.getTypeUtils().isAssignable(type, target);
    }

    public String qualifier(Element element) {
        Qualified qualified = element.getAnnotation(Qualified.class);
        return qualified == null ? "" : qualified.value();
    }

    public List<ExecutableElement> publicConstructors(TypeElement type) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor -> constructor.getModifiers().contains(Modifier.PUBLIC))
                .toList();
    }

    // Class values in annotations are only accessible as mirrors during processing
    public List<String> holders(Supplier<Class<?>[]> value) {
        try {
            value.get();
            return List.of();
        } catch (MirroredTypesException ex) {
            return ex.getTypeMirrors().stream().map(this::erased).toList();
        }
    }

    public boolean isOptional(TypeMirror type) {
        return erased(type).equals(Optional.class.getName());
    }

    public TypeMirror optionalArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType declared)
                || declared.getTypeArguments().size() != 1) return null;
        TypeMirror argument = declared.getTypeArguments().get(0);
        if (!(argument instanceof DeclaredType) || isOptional(argument)) return null;
        return argument;
    }

    public boolean isInstantiable(TypeElement component) {
        for (Element element = component;
                element instanceof TypeElement type;
                element = element.getEnclosingElement()) {
            if (type.getModifiers().contains(Modifier.PRIVATE)
                    || type.getModifiers().contains(Modifier.PROTECTED)) return false;
            if (type.getNestingKind() == NestingKind.MEMBER
                    && !type.getModifiers().contains(Modifier.STATIC)) return false;
            if (type.getNestingKind() != NestingKind.TOP_LEVEL && type.getNestingKind() != NestingKind.MEMBER)
                return false;
        }
        return true;
    }

    public boolean isPublic(TypeElement component) {
        for (Element element = component;
                element instanceof TypeElement type;
                element = element.getEnclosingElement()) {
            if (!type.getModifiers().contains(Modifier.PUBLIC)) return false;
        }
        return true;
    }

    // A public component keeps the stricter public constructor contract, a package visible one only
    // needs its constructor callable from its own package where the wiring fragment lives
    public List<ExecutableElement> accessibleConstructors(TypeElement type) {
        boolean publicOnly = isPublic(type);
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor -> publicOnly
                        ? constructor.getModifiers().contains(Modifier.PUBLIC)
                        : !constructor.getModifiers().contains(Modifier.PRIVATE))
                .toList();
    }

    public boolean isAccessibleFrom(TypeMirror type, String packageName) {
        for (Element element = asElement(processingEnv.getTypeUtils().erasure(type));
                element instanceof TypeElement typeElement;
                element = element.getEnclosingElement()) {
            if (typeElement.getModifiers().contains(Modifier.PUBLIC)) continue;
            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) return false;
            if (!packageName(typeElement).equals(packageName)) return false;
        }
        return true;
    }

    public String packageName(TypeMirror type) {
        return packageName((TypeElement) asElement(processingEnv.getTypeUtils().erasure(type)));
    }

    public boolean isReferencable(TypeMirror type) {
        for (Element element = asElement(processingEnv.getTypeUtils().erasure(type));
                element instanceof TypeElement typeElement;
                element = element.getEnclosingElement()) {
            if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) return false;
        }
        return true;
    }

    // Resolution is an exact type lookup, so only the declared type itself being a singleton counts
    public boolean isSingleton(TypeMirror type) {
        Element element = asElement(processingEnv.getTypeUtils().erasure(type));
        return element instanceof TypeElement singleton && isSingleton(singleton);
    }

    public boolean isSingleton(TypeElement component) {
        return component.getAnnotation(Singleton.class) != null;
    }

    public boolean isLazy(TypeElement component) {
        Singleton singleton = component.getAnnotation(Singleton.class);
        return singleton != null && singleton.lazy();
    }

    public boolean isSubtype(TypeElement type, String supertype) {
        TypeMirror mirror = mirror(supertype);
        return mirror != null && assignable(type.asType(), mirror);
    }

    public TypeMirror mirror(String qualifiedName) {
        TypeElement element = typeElement(qualifiedName);
        return element == null ? null : element.asType();
    }
}
