package org.weftkit.wiring.processor.generate;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.WildcardTypeName;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import javax.lang.model.util.Types;

// Renders an annotation as an anonymous implementation of its interface, defaults filled in, so the
// runtime reads recorded annotations without reflection
final class AnnotationRenderer {

    private static final TypeName ANNOTATION_TYPE =
            ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Annotation.class));

    private static final AnnotationSpec SUPPRESS_RAW_TYPES = AnnotationSpec.builder(SuppressWarnings.class)
            .addMember("value", "{$S, $S}", "rawtypes", "unchecked")
            .build();

    private final Elements elements;

    private final Types types;

    private final ValueVisitor values = new ValueVisitor();

    AnnotationRenderer(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    CodeBlock render(AnnotationMirror annotation) {
        TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
        ClassName typeName = ClassName.get(type);
        List<ExecutableElement> members = ElementFilter.methodsIn(type.getEnclosedElements());
        Map<? extends ExecutableElement, ? extends AnnotationValue> resolved =
                elements.getElementValuesWithDefaults(annotation);
        TypeSpec.Builder implementation = TypeSpec.anonymousClassBuilder("").addSuperinterface(typeName);
        for (ExecutableElement member : members) {
            TypeMirror returnType = member.getReturnType();
            MethodSpec.Builder method = override(member.getSimpleName().toString(), TypeName.get(returnType))
                    .addStatement("return $L", resolved.get(member).accept(values, returnType));
            if (needsRawTypes(returnType)) method.addAnnotation(SUPPRESS_RAW_TYPES);
            implementation.addMethod(method.build());
        }
        return CodeBlock.of(
                "$L",
                implementation
                        .addMethod(override("annotationType", ANNOTATION_TYPE)
                                .addStatement("return $T.class", typeName)
                                .build())
                        .addMethod(equalsMethod(typeName, members))
                        .addMethod(hashCodeMethod(members))
                        .addMethod(toStringMethod(typeName, members))
                        .build());
    }

    private static MethodSpec.Builder override(String name, TypeName returnType) {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType);
    }

    private static MethodSpec equalsMethod(ClassName type, List<ExecutableElement> members) {
        MethodSpec.Builder method = override("equals", TypeName.BOOLEAN)
                .addParameter(Object.class, "other")
                .addStatement("if (this == other) return true")
                .addStatement("if (!(other instanceof $T)) return false", type);
        if (members.isEmpty()) return method.addStatement("return true").build();
        return method.addStatement("$T that = ($T) other", type, type)
                .addStatement(
                        "return $L",
                        members.stream().map(AnnotationRenderer::equality).collect(CodeBlock.joining("\n && ")))
                .build();
    }

    private static CodeBlock equality(ExecutableElement member) {
        String name = member.getSimpleName().toString();
        return switch (member.getReturnType().getKind()) {
            case ARRAY -> CodeBlock.of("$1T.equals(this.$2N(), that.$2N())", Arrays.class, name);
            case FLOAT -> CodeBlock.of("$1T.compare(this.$2N(), that.$2N()) == 0", Float.class, name);
            case DOUBLE -> CodeBlock.of("$1T.compare(this.$2N(), that.$2N()) == 0", Double.class, name);
            case DECLARED -> CodeBlock.of("this.$1N().equals(that.$1N())", name);
            default -> CodeBlock.of("this.$1N() == that.$1N()", name);
        };
    }

    private static MethodSpec hashCodeMethod(List<ExecutableElement> members) {
        CodeBlock sum = members.stream()
                .map(member -> CodeBlock.of("(127 * $S.hashCode() ^ $L)", member.getSimpleName(), valueHash(member)))
                .collect(CodeBlock.joining("\n + "));
        return override("hashCode", TypeName.INT)
                .addStatement("return $L", members.isEmpty() ? "0" : sum)
                .build();
    }

    private static CodeBlock valueHash(ExecutableElement member) {
        String name = member.getSimpleName().toString();
        TypeMirror type = member.getReturnType();
        if (type.getKind() == TypeKind.ARRAY) return CodeBlock.of("$T.hashCode(this.$N())", Arrays.class, name);
        if (type.getKind().isPrimitive())
            return CodeBlock.of("$T.hashCode(this.$N())", TypeName.get(type).box(), name);
        return CodeBlock.of("this.$N().hashCode()", name);
    }

    private static MethodSpec toStringMethod(ClassName type, List<ExecutableElement> members) {
        CodeBlock values = members.stream()
                .map(member -> {
                    String name = member.getSimpleName().toString();
                    return member.getReturnType().getKind() == TypeKind.ARRAY
                            ? CodeBlock.of("$S + $T.toString(this.$N())", name + "=", Arrays.class, name)
                            : CodeBlock.of("$S + this.$N()", name + "=", name);
                })
                .collect(CodeBlock.joining(" + \", \" + "));
        return override("toString", ClassName.get(String.class))
                .addStatement(
                        "return $S + $L + $S",
                        "@" + type.canonicalName() + "(",
                        members.isEmpty() ? "\"\"" : values,
                        ")")
                .build();
    }

    private static boolean needsRawTypes(TypeMirror type) {
        return type instanceof ArrayType array ? !isReifiable(array.getComponentType()) : isRaw(type);
    }

    private static boolean isRaw(TypeMirror type) {
        return type instanceof DeclaredType declared
                && declared.getTypeArguments().isEmpty()
                && !((TypeElement) declared.asElement()).getTypeParameters().isEmpty();
    }

    private static boolean isReifiable(TypeMirror type) {
        return !(type instanceof DeclaredType declared)
                || (!isRaw(type) && declared.getTypeArguments().stream().allMatch(AnnotationRenderer::isUnbounded));
    }

    private static boolean isUnbounded(TypeMirror argument) {
        return argument instanceof WildcardType wildcard
                && wildcard.getExtendsBound() == null
                && wildcard.getSuperBound() == null;
    }

    private final class ValueVisitor extends SimpleAnnotationValueVisitor14<CodeBlock, TypeMirror> {

        @Override
        public CodeBlock visitBoolean(boolean value, TypeMirror type) {
            return CodeBlock.of("$L", value);
        }

        @Override
        public CodeBlock visitByte(byte value, TypeMirror type) {
            return CodeBlock.of("(byte) $L", value);
        }

        @Override
        public CodeBlock visitChar(char value, TypeMirror type) {
            return CodeBlock.of("(char) $L", (int) value);
        }

        @Override
        public CodeBlock visitShort(short value, TypeMirror type) {
            return CodeBlock.of("(short) $L", value);
        }

        @Override
        public CodeBlock visitInt(int value, TypeMirror type) {
            return CodeBlock.of("$L", value);
        }

        @Override
        public CodeBlock visitLong(long value, TypeMirror type) {
            return CodeBlock.of("$LL", value);
        }

        @Override
        public CodeBlock visitFloat(float value, TypeMirror type) {
            if (Float.isNaN(value)) return CodeBlock.of("$T.NaN", Float.class);
            if (Float.isInfinite(value))
                return CodeBlock.of("$T.$L", Float.class, value > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
            return CodeBlock.of("$LF", value);
        }

        @Override
        public CodeBlock visitDouble(double value, TypeMirror type) {
            if (Double.isNaN(value)) return CodeBlock.of("$T.NaN", Double.class);
            if (Double.isInfinite(value))
                return CodeBlock.of("$T.$L", Double.class, value > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
            return CodeBlock.of("$LD", value);
        }

        @Override
        public CodeBlock visitString(String value, TypeMirror type) {
            return CodeBlock.of("$S", value);
        }

        @Override
        public CodeBlock visitType(TypeMirror value, TypeMirror type) {
            return CodeBlock.of("$T.class", TypeName.get(types.erasure(value)));
        }

        @Override
        public CodeBlock visitEnumConstant(VariableElement value, TypeMirror type) {
            return CodeBlock.of("$T.$L", TypeName.get(value.asType()), value.getSimpleName());
        }

        @Override
        public CodeBlock visitAnnotation(AnnotationMirror value, TypeMirror type) {
            return render(value);
        }

        @Override
        public CodeBlock visitArray(List<? extends AnnotationValue> value, TypeMirror type) {
            TypeMirror component = ((ArrayType) type).getComponentType();
            CodeBlock entries =
                    value.stream().map(entry -> entry.accept(this, component)).collect(CodeBlock.joining(", "));
            TypeName created =
                    isReifiable(component) ? TypeName.get(component) : TypeName.get(types.erasure(component));
            return CodeBlock.of("new $T[] {$L}", created, entries);
        }
    }
}
