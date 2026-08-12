package org.weftkit.wiring.processor.generate;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.function.Function;
import java.util.function.Supplier;

// One registry section: its accessor names both the interface method and, upper-cased, the
// backing constant
record Section(
        String accessor,
        TypeName type,
        Container container,
        Supplier<CodeBlock> centralEntries,
        Function<Fragment, CodeBlock> fragmentEntries) {

    String constant() {
        return constantFor(accessor);
    }

    static String constantFor(String accessor) {
        StringBuilder constant = new StringBuilder();
        for (char character : accessor.toCharArray()) {
            if (Character.isUpperCase(character)) constant.append('_');
            constant.append(Character.toUpperCase(character));
        }
        return constant.toString();
    }

    boolean presentIn(Fragment fragment) {
        return !fragmentEntries.apply(fragment).isEmpty();
    }
}
