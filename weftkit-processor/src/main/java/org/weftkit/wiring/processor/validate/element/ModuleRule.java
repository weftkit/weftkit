package org.weftkit.wiring.processor.validate.element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.FeatureFlag;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;
import org.weftkit.wiring.processor.spi.ComponentRule;
import org.weftkit.wiring.processor.spi.ModuleContributor;

/**
 * Resolves {@code @Registry} module declarations, adapter contributions, and disables into the
 * woven module set, collecting each surviving module and its components into the model. Modules
 * are read from the compile classpath, so validation runs against class files, anchored on the
 * registry element.
 */
public final class ModuleRule {

    private static final String LOADER = Loader.class.getCanonicalName();

    private final ProcessingEnvironment processingEnv;

    private final Mirrors mirrors;

    private final Diagnostics diagnostics;

    private final ModelCollector collector;

    private final ProvidesRule providesRule;

    private final List<ComponentRule> componentRules;

    private final List<String> declared = new ArrayList<>();

    private final List<String> disabled = new ArrayList<>();

    private final Map<String, String> disabledModules = new HashMap<>();

    private TypeElement registry;

    public ModuleRule(
            ProcessingEnvironment processingEnv,
            Mirrors mirrors,
            Diagnostics diagnostics,
            ModelCollector collector,
            ProvidesRule providesRule,
            List<ComponentRule> componentRules) {
        this.processingEnv = processingEnv;
        this.mirrors = mirrors;
        this.diagnostics = diagnostics;
        this.collector = collector;
        this.providesRule = providesRule;
        this.componentRules = componentRules;
    }

    /** Records the registry's module declarations and disables, resolved later by {@link #resolve}. */
    public void declare(TypeElement registry, List<String> modules, String[] disabledIds) {
        this.registry = registry;
        declared.addAll(modules);
        disabled.addAll(List.of(disabledIds));
    }

    /**
     * Resolves declared and adapter-contributed modules into the model. Contributions come from
     * {@link ModuleContributor} services on the processor path; a contributed class missing from
     * the compile classpath is skipped, its adapter is not in use. Declarations win over
     * contributions with the same id and handle their annotations, disabled ids drop the module
     * and its components, and an unknown disabled id is an error.
     */
    public void resolve() {
        for (String name : contributedAmbient()) {
            TypeElement element = mirrors.typeElement(name);
            if (element != null) collector.addAmbientType(element.asType());
        }
        List<String> contributed = contributions();
        Map<String, TypeElement> selected = new LinkedHashMap<>();
        Map<String, String> moduleIds = new LinkedHashMap<>();
        Map<String, String> byId = new HashMap<>();
        for (String name : declared) {
            TypeElement element = mirrors.typeElement(name);
            if (element == null) {
                diagnostics.error(registry, "Cannot resolve the declared module " + name);
                continue;
            }
            String id = id(element, name);
            if (id == null) continue;
            if (selected.containsKey(name)) {
                diagnostics.error(registry, "Module " + name + " is declared twice");
                continue;
            }
            String clash = byId.putIfAbsent(id, name);
            if (clash != null) {
                diagnostics.error(registry, "Modules " + clash + " and " + name + " share the id " + id);
                continue;
            }
            selected.put(name, element);
            moduleIds.put(name, id);
        }
        Set<String> declaredIds = Set.copyOf(byId.keySet());
        for (String name : contributed) {
            TypeElement element = mirrors.typeElement(name);
            if (element == null || selected.containsKey(name)) continue;
            String id = id(element, name);
            if (id == null) continue;
            String clash = byId.putIfAbsent(id, name);
            if (clash != null) {
                collector.replaceModule(element, selected.get(clash));
                if (!declaredIds.contains(id))
                    diagnostics.warning(
                            registry,
                            "Contributed modules " + clash + " and " + name + " share the id " + id + ", keeping "
                                    + clash);
                continue;
            }
            selected.put(name, element);
            moduleIds.put(name, id);
        }
        for (String id : disabled)
            if (!byId.containsKey(id))
                diagnostics.error(registry, "@Registry(disable) names an unknown module id: " + id);
        for (String name : selected.keySet())
            if (disabled.contains(moduleIds.get(name))) disabledModules.put(name, moduleIds.get(name));
        selected.keySet().removeIf(disabledModules::containsKey);
        for (TypeElement module : selected.values()) collectModule(module);
    }

    /** The disabled modules by class name, each mapped to its id. Filled by {@link #resolve}. */
    public Map<String, String> disabledModules() {
        return disabledModules;
    }

    private static List<String> contributions() {
        List<String> contributed = new ArrayList<>();
        for (ModuleContributor contributor : contributors()) contributed.addAll(contributor.modules());
        return contributed;
    }

    private static List<String> contributedAmbient() {
        List<String> types = new ArrayList<>();
        for (ModuleContributor contributor : contributors()) types.addAll(contributor.ambientTypes());
        return types;
    }

    private static ServiceLoader<ModuleContributor> contributors() {
        return ServiceLoader.load(ModuleContributor.class, ModuleContributor.class.getClassLoader());
    }

    private String id(TypeElement element, String name) {
        WeftModule annotation = element.getAnnotation(WeftModule.class);
        if (annotation == null) {
            diagnostics.error(registry, "Module " + name + " is not annotated with @WeftModule");
            return null;
        }
        if (annotation.id().isBlank()) {
            diagnostics.error(registry, "Module " + name + " declares a blank id");
            return null;
        }
        return annotation.id();
    }

    private void collectModule(TypeElement module) {
        String name = module.getQualifiedName().toString();
        List<ExecutableElement> constructors = mirrors.publicConstructors(module);
        boolean valid = diagnostics
                .check(registry)
                .require(
                        module.getKind() == ElementKind.CLASS
                                && !module.getModifiers().contains(Modifier.ABSTRACT),
                        "Module " + name + " must be a concrete class")
                .require(module.getTypeParameters().isEmpty(), "Module " + name + " cannot be generic")
                .require(
                        mirrors.isPublic(module) && mirrors.isInstantiable(module),
                        "Module " + name + " must be public for the generated registry to instantiate")
                .require(constructors.size() == 1, "Module " + name + " needs exactly one public constructor")
                .require(
                        module.getAnnotation(Wired.class) == null && module.getAnnotation(Singleton.class) == null,
                        "Module " + name + " is wired implicitly, drop @Wired and @Singleton")
                .require(
                        module.getAnnotation(FeatureFlag.class) == null,
                        "Module " + name + " cannot be feature flagged, opt out with @Registry(disable)")
                .passed();
        if (!valid) return;
        collector.collectModule(module, constructors.get(0));
        WeftModule annotation = module.getAnnotation(WeftModule.class);
        for (String component : mirrors.holders(annotation::components)) collectComponent(name, component);
    }

    private void collectComponent(String module, String name) {
        TypeElement component = mirrors.typeElement(name);
        if (component == null) {
            diagnostics.error(registry, "Cannot resolve component " + name + " of module " + module);
            return;
        }
        // Already collected as a source component or through another module
        if (collector.isCollected(name)) return;
        List<ExecutableElement> constructors = mirrors.publicConstructors(component);
        String label = "Component " + name + " of module " + module;
        boolean valid = diagnostics
                .check(registry)
                .require(component.getAnnotation(Wired.class) != null, label + " must be @Wired")
                .require(component.getAnnotation(WeftModule.class) == null, label + " cannot be a module itself")
                .require(
                        component.getKind() == ElementKind.CLASS
                                && !component.getModifiers().contains(Modifier.ABSTRACT),
                        label + " must be a concrete class")
                .require(component.getTypeParameters().isEmpty(), label + " cannot be generic")
                .require(mirrors.isPublic(component) && mirrors.isInstantiable(component), label + " must be public")
                .require(constructors.size() == 1, label + " needs exactly one public constructor")
                .require(
                        !mirrors.isSubtype(component, LOADER) || mirrors.isSingleton(component),
                        label + " implements Loader and must be @Singleton")
                .require(
                        component.getAnnotation(Requires.class) == null
                                && component.getAnnotation(Initializes.class) == null,
                        label + " cannot use @Requires or @Initializes, static holder analysis is source only")
                .passed();
        if (!valid) return;
        for (ComponentRule rule : componentRules) rule.validate(component, processingEnv);
        collector.collect(component, constructors.get(0));
        for (ExecutableElement getter : ElementFilter.methodsIn(component.getEnclosedElements()))
            if (getter.getAnnotation(Provides.class) != null) providesRule.validate(getter);
    }
}
