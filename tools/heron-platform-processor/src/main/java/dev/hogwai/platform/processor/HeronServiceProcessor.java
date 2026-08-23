package dev.hogwai.platform.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.host.HostAdapter;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import dev.hogwai.platform.spi.registry.GenerationStore;

/**
 * Annotation processor that generates {@code META-INF/services} descriptors for classes annotated with {@link HeronService}.
 *
 * <p>For every annotated class the processor verifies that it is a public,
 * non-abstract class with a public no-argument constructor implementing the service contract declared in the annotation,
 * and that one of the supported contracts ({@link ProviderFactory}, {@link DataAccessFactory}, {@link HostAdapter}
 * or {@link GenerationStore}) is named.
 * It also validates the declared metadata:
 * the identifier must be non-blank and free of whitespace, the version must be a canonical {@code major.minor.patch} string,
 * and two services of the same contract may not declare the same identifier inside a single compilation.
 * Descriptors are emitted once at the end of the last processing round and are sorted for deterministic output.
 */
@SupportedAnnotationTypes(HeronServiceProcessor.ANNOTATION)
public final class HeronServiceProcessor extends AbstractProcessor {

    /** Fully qualified name of the annotation triggering this processor. */
    public static final String ANNOTATION = "dev.hogwai.platform.spi.annotation.HeronService";

    @SuppressWarnings("java:S6353")
    private static final Pattern CANONICAL_VERSION =
            Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    private static final List<String> SUPPORTED_CONTRACTS = List.of(
            ProviderFactory.class.getName(),
            DataAccessFactory.class.getName(),
            HostAdapter.class.getName(),
            GenerationStore.class.getName());

    private final SortedMap<String, SortedSet<String>> registrations = new TreeMap<>();
    private final Map<String, String> declaredIds = new HashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(HeronService.class)) {
            validate(element);
        }
        if (roundEnvironment.processingOver()) {
            DescriptorWriter.writeDescriptors(registrations, processingEnv.getFiler(), processingEnv.getMessager());
        }
        return true;
    }

    private void validate(Element element) {
        if (!(element instanceof TypeElement type)) {
            fail(element, "must be applied to a class");
            return;
        }
        Optional<Declaration> declaration = Declaration.from(type);
        if (declaration.isEmpty()) {
            fail(type, "could not be read: recompile the module to refresh annotation metadata");
            return;
        }
        Declaration service = declaration.get();
        Optional<String> structuralFailure = Structure.check(type);
        if (structuralFailure.isPresent()) {
            fail(type, structuralFailure.get());
            return;
        }
        Optional<String> metadataFailure = MetadataChecks.failure(service);
        if (metadataFailure.isPresent()) {
            fail(type, metadataFailure.get());
            return;
        }
        String contract = service.contractQualifiedName();
        if (!SUPPORTED_CONTRACTS.contains(contract)) {
            fail(type, "declares unsupported service contract %s supported contracts are %s"
                    .formatted(contract, String.join(", ", SUPPORTED_CONTRACTS)));
            return;
        }
        Types types = processingEnv.getTypeUtils();
        if (!types.isAssignable(type.asType(), service.contractType())) {
            fail(type, "does not implement its declared service contract %s".formatted(contract));
            return;
        }
        register(contract, service, type);
    }

    private void register(String contract, Declaration service, TypeElement type) {
        String implementation = type.getQualifiedName().toString();
        String key = contract + '#' + service.id();
        String previous = declaredIds.putIfAbsent(key, implementation);
        if (previous != null) {
            fail(type, "declares duplicate id '%s' for contract %s (already declared by %s)"
                    .formatted(service.id(), contract, previous));
            return;
        }
        registrations.computeIfAbsent(contract, name -> new TreeSet<>()).add(implementation);
    }

    private void fail(Element element, String reason) {
        processingEnv
                .getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "@HeronService target %s".formatted(reason), element);
    }

    /**
     * Private structural checks verifying that an annotated type can be
     * loaded through {@code ServiceLoader}.
     */
    private static final class Structure {

        private Structure() {
            // no instances
        }

        static Optional<String> check(TypeElement type) {
            if (type.getKind() != ElementKind.CLASS) {
                return Optional.of("must be a plain class");
            }
            Set<Modifier> modifiers = type.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC)) {
                return Optional.of("must be public to be loaded through ServiceLoader");
            }
            if (modifiers.contains(Modifier.ABSTRACT)) {
                return Optional.of("must not be abstract to be loaded through ServiceLoader");
            }
            return publicNoArgConstructor(type);
        }

        private static Optional<String> publicNoArgConstructor(TypeElement type) {
            List<ExecutableElement> constructors = new ArrayList<>();
            for (Element enclosed : type.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                    constructors.add((ExecutableElement) enclosed);
                }
            }
            if (constructors.isEmpty()) {
                // No explicit constructor: javac generates a public default one.
                return Optional.empty();
            }
            for (ExecutableElement constructor : constructors) {
                if (constructor.getParameters().isEmpty()
                        && constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    return Optional.empty();
                }
            }
            return Optional.of("must declare a public no-argument constructor required by ServiceLoader");
        }
    }

    /**
     * Private holder of the annotation values read through the annotation
     * mirror so that class-valued members never trigger
     * {@code MirroredTypeException}.
     */
    private record Declaration(TypeMirror contractType, String id, String version) {

        static Optional<Declaration> from(TypeElement type) {
            for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
                if (mirror.getAnnotationType().toString().equals(ANNOTATION)) {
                    return from(mirror.getElementValues());
                }
            }
            return Optional.empty();
        }

        private static Optional<Declaration> from(
                Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
            TypeMirror contractType = mirrorValue(values, "value", TypeMirror.class);
            String id = mirrorValue(values, "id", String.class);
            String version = mirrorValue(values, "version", String.class);
            if (contractType == null || id == null) {
                return Optional.empty();
            }
            // getElementValues() only carries explicit values: apply the
            // annotation default when the member was omitted at the use site.
            String effectiveVersion = version == null ? "1.0.0" : version;
            return Optional.of(new Declaration(contractType, id, effectiveVersion));
        }

        private static <T> T mirrorValue(Map<? extends ExecutableElement, ? extends AnnotationValue> values,
                                         String memberName, Class<T> memberType) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
                if (memberName.equals(entry.getKey().getSimpleName().toString())) {
                    return memberType.cast(entry.getValue().getValue());
                }
            }
            return null;
        }

        String contractQualifiedName() {
            return contractType.toString();
        }
    }

    /**
     * Private metadata checks mirroring the {@code ProviderId} and
     * {@code ProviderVersion} contracts.
     */
    private static final class MetadataChecks {

        private MetadataChecks() {
            // no instances
        }

        static Optional<String> failure(Declaration declaration) {
            if (declaration.id().isBlank()) {
                return Optional.of("must declare a non-blank id");
            }
            if (containsWhitespace(declaration.id())) {
                return Optional.of("id must not contain whitespace: '%s'".formatted(declaration.id()));
            }
            if (!CANONICAL_VERSION.matcher(declaration.version()).matches()) {
                return Optional.of("version must be a canonical major.minor.patch string: '%s'"
                        .formatted(declaration.version()));
            }
            return Optional.empty();
        }

        private static boolean containsWhitespace(String value) {
            return value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
        }
    }

    /**
     * Private writer emitting the accumulated descriptors at the end of the
     * last processing round.
     */
    private static final class DescriptorWriter {

        private static final String SERVICES_DIRECTORY = "META-INF/services/";

        private DescriptorWriter() {
            // no instances
        }

        static void writeDescriptors(Map<String, SortedSet<String>> registrations,
                                     Filer filer, Messager messager) {
            for (Map.Entry<String, SortedSet<String>> registration : registrations.entrySet()) {
                writeDescriptor(registration.getKey(), registration.getValue(), filer, messager);
            }
        }

        private static void writeDescriptor(String contract, SortedSet<String> implementations,
                                            Filer filer, Messager messager) {
            try {
                FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                        SERVICES_DIRECTORY + contract);
                try (PrintWriter writer = new PrintWriter(resource.openWriter())) {
                    for (String implementation : implementations) {
                        writer.println(implementation);
                    }
                }
            } catch (IOException failure) {
                messager.printMessage(Diagnostic.Kind.ERROR, "failed to write service descriptor %s%s: %s"
                        .formatted(SERVICES_DIRECTORY, contract, failure.getMessage()));
            }
        }
    }
}
