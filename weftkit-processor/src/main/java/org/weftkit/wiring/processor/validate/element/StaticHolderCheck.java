package org.weftkit.wiring.processor.validate.element;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.lang.reflect.Field;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.weftkit.wiring.StaticHolder;

// The scan needs javac's Tree API, so it degrades to a no-op under other compilers and only sees
// direct accesses in this compilation; helper methods, reflection, and foreign jars stay invisible
class StaticHolderCheck {

    private final Trees trees;

    StaticHolderCheck(ProcessingEnvironment processingEnv) {
        this.trees = resolveTrees(processingEnv);
    }

    void check(TypeElement component, ExecutableElement constructor, boolean loader, Set<String> declared) {
        if (trees == null) return;
        scan(constructor, declared);
        if (loader)
            for (ExecutableElement method : ElementFilter.methodsIn(component.getEnclosedElements()))
                if (method.getSimpleName().contentEquals("load")
                        && method.getParameters().isEmpty()) scan(method, declared);
        for (VariableElement field : ElementFilter.fieldsIn(component.getEnclosedElements())) scan(field, declared);
    }

    private void scan(Element element, Set<String> declared) {
        TreePath path = trees.getPath(element);
        if (path != null) new HolderScanner(declared).scan(path, null);
    }

    // Gradle wraps the environment for incremental processing, so unwrap until javac's own shows up
    private static Trees resolveTrees(ProcessingEnvironment processingEnv) {
        ProcessingEnvironment current = processingEnv;
        for (int depth = 0; current != null && depth < 5; depth++) {
            try {
                return Trees.instance(current);
            } catch (RuntimeException ex) {
                current = delegate(current);
            }
        }
        return null;
    }

    private static ProcessingEnvironment delegate(ProcessingEnvironment processingEnv) {
        for (Class<?> type = processingEnv.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ProcessingEnvironment.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    return (ProcessingEnvironment) field.get(processingEnv);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    continue;
                }
            }
        }
        return null;
    }

    private final class HolderScanner extends TreePathScanner<Void, Void> {

        private final Set<String> declared;

        private HolderScanner(Set<String> declared) {
            this.declared = declared;
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            inspect();
            return null;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            inspect();
            return super.visitMemberSelect(tree, unused);
        }

        @SuppressWarnings("deprecation")
        private void inspect() {
            Element element = trees.getElement(getCurrentPath());
            if (element == null || !element.getModifiers().contains(Modifier.STATIC)) return;
            ElementKind kind = element.getKind();
            if (kind != ElementKind.FIELD && kind != ElementKind.METHOD && kind != ElementKind.ENUM_CONSTANT) return;
            if (!(element.getEnclosingElement() instanceof TypeElement holder)
                    || holder.getAnnotation(StaticHolder.class) == null) return;
            if (declared.contains(holder.getQualifiedName().toString())) return;
            trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Static holder is accessed during load without @Requires: " + holder.getQualifiedName(),
                    getCurrentPath().getLeaf(),
                    getCurrentPath().getCompilationUnit());
        }
    }
}
