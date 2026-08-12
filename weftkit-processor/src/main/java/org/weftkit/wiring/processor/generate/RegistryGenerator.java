package org.weftkit.wiring.processor.generate;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import org.weftkit.wiring.processor.WiredProcessor;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.Product;
import org.weftkit.wiring.processor.model.WiringModel;
import org.weftkit.wiring.runtime.ComponentRegistry;
import org.weftkit.wiring.runtime.Dependency;
import org.weftkit.wiring.runtime.Registries;

public class RegistryGenerator {

    private static final String REGISTRY_NAME = "WeftWiring";

    private static final String FACTORIES_SECTION = "factories";

    private static final ClassName COMPONENT_REGISTRY = ClassName.get(ComponentRegistry.class);

    private static final ClassName DEPENDENCY = ClassName.get(Dependency.class);

    private static final ClassName REGISTRIES = ClassName.get(Registries.class);

    private static final TypeName OBJECT = ClassName.get(Object.class);

    private static final TypeName ANY_CLASS =
            ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class));

    private static final TypeName CLASS_LIST = ParameterizedTypeName.get(ClassName.get(List.class), ANY_CLASS);

    private static final TypeName CLASS_SET = ParameterizedTypeName.get(ClassName.get(Set.class), ANY_CLASS);

    private static final TypeName FACTORY =
            ParameterizedTypeName.get(ClassName.get(Function.class), ArrayTypeName.of(OBJECT), OBJECT);

    private static final TypeName GETTER = ParameterizedTypeName.get(ClassName.get(Function.class), OBJECT, OBJECT);

    private final WiringModel model;

    private final String registryPackage;

    private final Fragments fragments;

    private final List<Section> sections;

    public RegistryGenerator(WiringModel model, String registryPackage) {
        this.model = model;
        this.registryPackage = registryPackage;
        this.fragments = new Fragments(model, registryPackage);
        this.sections = sections();
    }

    public void write(ProcessingEnvironment processingEnv, Element... originating) {
        writeFile(processingEnv, registryPackage, centralType(originating), "");
        fragments.byPackage().forEach((packageName, fragment) -> {
            String name = packageName + "." + REGISTRY_NAME;
            if (processingEnv.getElementUtils().getTypeElement(name) != null) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                "weftkit cannot generate " + name + " because the class already exists");
                return;
            }
            writeFile(
                    processingEnv,
                    packageName,
                    fragmentType(fragment, originating),
                    "Internal wiring for this package's non-public components, consumed by the\n"
                            + "$L registry. Not API, offers no compatibility guarantees",
                    registryPackage);
        });
    }

    private void writeFile(
            ProcessingEnvironment processingEnv,
            String packageName,
            TypeSpec type,
            String fileComment,
            Object... commentArgs) {
        JavaFile.Builder builder =
                JavaFile.builder(packageName, type).indent("    ").skipJavaLangImports(true);
        if (!fileComment.isEmpty()) builder.addFileComment(fileComment, commentArgs);
        JavaFile file = builder.build();
        try {
            file.writeTo(processingEnv.getFiler());
        } catch (IOException ex) {
            processingEnv
                    .getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Failed to generate component registry: " + ex.getMessage());
        }
    }

    private static String processorName() {
        return WiredProcessor.class.getName();
    }

    private List<Section> sections() {
        return List.of(
                new Section(
                        "lazySingletons",
                        CLASS_SET,
                        Container.SET,
                        () -> lazyEntries(fragments.centralLazySingletons()),
                        fragment -> lazyEntries(fragment.lazySingletons())),
                new Section(
                        "parameters",
                        mapOf(ParameterizedTypeName.get(ClassName.get(List.class), DEPENDENCY)),
                        Container.MAP,
                        () -> parameterEntries(centralComponents()),
                        fragment -> parameterEntries(fragment.components())),
                new Section(
                        FACTORIES_SECTION,
                        mapOf(FACTORY),
                        Container.MAP,
                        () -> factoryEntries(centralComponents()),
                        fragment -> factoryEntries(fragment.components())),
                new Section(
                        "productOwners",
                        mapOf(qualifiedMap(ANY_CLASS)),
                        Container.NESTED_MAP,
                        () -> productOwnerEntries(fragments.centralProducts()),
                        fragment -> productOwnerEntries(fragment.products())),
                new Section(
                        "productGetters",
                        mapOf(qualifiedMap(GETTER)),
                        Container.NESTED_MAP,
                        () -> productGetterEntries(fragments.centralProducts()),
                        fragment -> productGetterEntries(fragment.products())),
                new Section(
                        "bindings",
                        mapOf(qualifiedMap(ANY_CLASS)),
                        Container.NESTED_MAP,
                        () -> bindingEntries(fragments.centralBindings()),
                        fragment -> bindingEntries(fragment.bindings())));
    }

    private static TypeName mapOf(TypeName value) {
        return ParameterizedTypeName.get(ClassName.get(Map.class), ANY_CLASS, value);
    }

    private static TypeName qualifiedMap(TypeName value) {
        return ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), value);
    }

    private TypeSpec centralType(Element... originating) {
        TypeSpec.Builder type = TypeSpec.classBuilder(REGISTRY_NAME)
                .addModifiers(Modifier.FINAL)
                .addSuperinterface(COMPONENT_REGISTRY)
                .addAnnotation(generated())
                .addField(FieldSpec.builder(COMPONENT_REGISTRY, "INSTANCE", Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $T()", ClassName.get(registryPackage, REGISTRY_NAME))
                        .build());
        if (needsUnchecked(centralComponents())) type.addAnnotation(suppressUnchecked());
        for (Section section : sections) {
            type.addField(FieldSpec.builder(
                            section.type(), section.constant(), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(centralInitializer(section))
                    .build());
            // The load order must initialize after FACTORIES, whose keys resolve hidden names
            if (section.accessor().equals(FACTORIES_SECTION)) type.addField(loadOrderField());
        }
        type.addMethod(
                MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
        type.addMethod(accessor("loadOrder", CLASS_LIST, "LOAD_ORDER"));
        for (Section section : sections)
            type.addMethod(accessor(section.accessor(), section.type(), section.constant()));
        for (Element element : originating) type.addOriginatingElement(element);
        return type.build();
    }

    private TypeSpec fragmentType(Fragment fragment, Element... originating) {
        TypeSpec.Builder type = TypeSpec.classBuilder(REGISTRY_NAME)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(generated());
        if (needsUnchecked(fragment.components())) type.addAnnotation(suppressUnchecked());
        for (Section section : sections) {
            CodeBlock entries = section.fragmentEntries().apply(fragment);
            if (entries.isEmpty()) continue;
            type.addField(FieldSpec.builder(
                            section.type(), section.constant(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer(container(section, entries))
                    .build());
        }
        type.addMethod(
                MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
        for (Element element : originating) type.addOriginatingElement(element);
        return type.build();
    }

    private CodeBlock centralInitializer(Section section) {
        CodeBlock base = container(section, section.centralEntries().get());
        List<String> packages = contributing(section);
        if (packages.isEmpty()) return base;
        CodeBlock.Builder merged = CodeBlock.builder()
                .add("$T.$L($L", REGISTRIES, section.container().merger(), base);
        for (String packageName : packages)
            merged.add(", $T.$L", ClassName.get(packageName, REGISTRY_NAME), section.constant());
        return merged.add(")").build();
    }

    private static CodeBlock container(Section section, CodeBlock entries) {
        return CodeBlock.of(
                "$T.$L(\n$L)", section.container().type(), section.container().factory(), entries);
    }

    private FieldSpec loadOrderField() {
        return FieldSpec.builder(CLASS_LIST, "LOAD_ORDER", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.of(\n$L)", List.class, loadOrderEntries())
                .build();
    }

    private static MethodSpec accessor(String name, TypeName type, String constant) {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return $L", constant)
                .build();
    }

    // Factory casts to a parameterized type are the only unchecked operations the registry can
    // contain, components themselves are never generic
    private static boolean needsUnchecked(Map<String, Component> components) {
        return components.values().stream()
                .flatMap(definition -> definition.parameters().stream())
                .anyMatch(parameter -> parameter.declaredType().contains("<"));
    }

    private static AnnotationSpec generated() {
        return AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", processorName())
                .build();
    }

    private static AnnotationSpec suppressUnchecked() {
        return AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unchecked")
                .build();
    }

    private List<String> contributing(Section section) {
        return fragments.byPackage().entrySet().stream()
                .filter(entry -> section.presentIn(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<String, Component> centralComponents() {
        Map<String, Component> central = new LinkedHashMap<>();
        model.components().forEach((component, definition) -> {
            if (!fragments.isFragmented(component)) central.put(component, definition);
        });
        return central;
    }

    private CodeBlock loadOrderEntries() {
        return model.loadOrder().stream()
                .map(singleton -> fragments.fragmentPackage(singleton) == null
                        ? CodeBlock.of("$L.class", singleton)
                        : CodeBlock.of(
                                "$T.type($L, $S)",
                                REGISTRIES,
                                Section.constantFor(FACTORIES_SECTION),
                                binaryName(singleton)))
                .collect(CodeBlock.joining(",\n"));
    }

    // Class.getName returns binary names, so nested hidden types need their dots swapped for dollars
    private String binaryName(String type) {
        String packageName = model.hiddenPackage(type);
        return packageName + "." + type.substring(packageName.length() + 1).replace('.', '$');
    }

    private CodeBlock lazyEntries(List<String> singletons) {
        return singletons.stream().map(RegistryGenerator::classLiteral).collect(CodeBlock.joining(",\n"));
    }

    private CodeBlock parameterEntries(Map<String, Component> components) {
        return components.entrySet().stream()
                .map(component -> mapEntry(
                        component.getKey(), dependencyList(component.getValue().parameters())))
                .collect(CodeBlock.joining(",\n"));
    }

    private CodeBlock dependencyList(List<Parameter> parameters) {
        CodeBlock dependencies =
                parameters.stream().map(RegistryGenerator::dependency).collect(CodeBlock.joining(", "));
        return CodeBlock.of("$T.<$T>of($L)", List.class, DEPENDENCY, dependencies);
    }

    private static CodeBlock dependency(Parameter parameter) {
        return CodeBlock.of(
                "new $T($L.class, $S, $L)",
                DEPENDENCY,
                parameter.erasedClass(),
                parameter.qualifier(),
                parameter.optional());
    }

    private CodeBlock factoryEntries(Map<String, Component> components) {
        return components.entrySet().stream()
                .map(component -> mapEntry(component.getKey(), factory(component.getKey(), component.getValue())))
                .collect(CodeBlock.joining(",\n"));
    }

    private CodeBlock factory(String component, Component definition) {
        CodeBlock arguments = IntStream.range(0, definition.parameters().size())
                .mapToObj(index -> factoryArgument(definition.parameters().get(index), index))
                .collect(CodeBlock.joining(", "));
        return CodeBlock.of("arguments -> new $L($L)", component, arguments);
    }

    private CodeBlock factoryArgument(Parameter parameter, int index) {
        CodeBlock cast = CodeBlock.of("($L) arguments[$L]", parameter.declaredType(), index);
        return parameter.optional() ? CodeBlock.of("$T.ofNullable($L)", Optional.class, cast) : cast;
    }

    private CodeBlock productOwnerEntries(Map<String, Map<String, Product>> products) {
        return products.entrySet().stream()
                .map(product -> mapEntry(
                        product.getKey(),
                        qualifiedValues(ANY_CLASS, product.getValue(), provider -> classLiteral(provider.owner()))))
                .collect(CodeBlock.joining(",\n"));
    }

    private CodeBlock productGetterEntries(Map<String, Map<String, Product>> products) {
        return products.entrySet().stream()
                .map(product -> mapEntry(
                        product.getKey(), qualifiedValues(GETTER, product.getValue(), RegistryGenerator::getter)))
                .collect(CodeBlock.joining(",\n"));
    }

    private static CodeBlock getter(Product product) {
        return CodeBlock.of("owner -> (($L) owner).$L()", product.owner(), product.getter());
    }

    private CodeBlock bindingEntries(Map<String, Map<String, String>> bindings) {
        return bindings.entrySet().stream()
                .map(binding -> mapEntry(
                        binding.getKey(),
                        qualifiedValues(ANY_CLASS, binding.getValue(), RegistryGenerator::classLiteral)))
                .collect(CodeBlock.joining(",\n"));
    }

    private static CodeBlock mapEntry(String type, CodeBlock value) {
        return CodeBlock.of("$T.entry($L.class, $L)", Map.class, type, value);
    }

    private static CodeBlock classLiteral(String type) {
        return CodeBlock.of("$L.class", type);
    }

    private static <V> CodeBlock qualifiedValues(
            TypeName valueType, Map<String, V> qualified, Function<V, CodeBlock> value) {
        CodeBlock pairs = qualified.entrySet().stream()
                .map(entry -> CodeBlock.of("$S, $L", entry.getKey(), value.apply(entry.getValue())))
                .collect(CodeBlock.joining(", "));
        return CodeBlock.of("$T.<$T, $T>of($L)", Map.class, String.class, valueType, pairs);
    }
}
