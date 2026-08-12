package org.weftkit.wiring.processor;

import com.google.auto.service.AutoService;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Qualified;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.generate.GraphGenerator;
import org.weftkit.wiring.processor.generate.RegistryGenerator;
import org.weftkit.wiring.processor.model.WiringModel;
import org.weftkit.wiring.processor.validate.WiredValidator;

@AutoService(Processor.class)
public class WiredProcessor extends AbstractProcessor {

    private final WiringModel model = new WiringModel();

    private WiredValidator validator;

    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        validator = new WiredValidator(processingEnv, model);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                Registry.class.getCanonicalName(),
                Wired.class.getCanonicalName(),
                Singleton.class.getCanonicalName(),
                Provides.class.getCanonicalName(),
                Initializes.class.getCanonicalName(),
                Requires.class.getCanonicalName(),
                Qualified.class.getCanonicalName(),
                StaticHolder.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        validator.addSources(roundEnvironment);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Registry.class))
            validator.validateRegistry((TypeElement) element);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Wired.class))
            validator.validateComponent((TypeElement) element);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Singleton.class))
            validator.validateSingleton((TypeElement) element);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Provides.class))
            validator.validateProduct((ExecutableElement) element);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Initializes.class))
            validator.validateInitializes((TypeElement) element);
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Requires.class))
            validator.validateRequires((TypeElement) element);
        // Emit the registry only once and before the final round so it still gets processed itself
        if (!generated && !model.isEmpty() && !roundEnvironment.processingOver()) {
            validator.validateGraph();
            if (validator.registryPackage() != null) {
                Element[] originating = originatingElements();
                new RegistryGenerator(model, validator.registryPackage()).write(processingEnv, originating);
                new GraphGenerator(model, validator.registryPackage()).write(processingEnv, originating);
            }
            generated = true;
        }
        return true;
    }

    // The generated files aggregate every component and the registry class, so name them all as origins
    private Element[] originatingElements() {
        return Stream.concat(model.components().keySet().stream(), Stream.ofNullable(validator.registryClass()))
                .map(processingEnv.getElementUtils()::getTypeElement)
                .filter(Objects::nonNull)
                .toArray(Element[]::new);
    }
}
