package dev.hogwai.platform.spi.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Package-private test helper asserting the exact public API of a context type.
 *
 * <p>Verifies exactly one public constructor with the given parameter types, no
 * public fields, and exactly the declared public methods with full signatures
 * (name, parameter types and return type). Any added logger, metrics, event
 * sink, secret String or raw payload member fails the assertion.
 */
final class ApiAssert {

    private ApiAssert() {
        // no instances
    }

    static void assertPublicApi(Class<?> type, Class<?>[] constructorParams,
                                Set<MethodSpec> expectedMethods) {
        assertSinglePublicConstructor(type, constructorParams);
        assertNoPublicFields(type);
        assertExactPublicMethods(type, expectedMethods);
    }

    private static void assertSinglePublicConstructor(Class<?> type, Class<?>[] params) {
        Constructor<?>[] constructors = publicConstructors(type);
        assertThat(constructors).as("public constructors of %s", type.getSimpleName()).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(params);
    }

    private static Constructor<?>[] publicConstructors(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(c -> Modifier.isPublic(c.getModifiers()))
                .toArray(Constructor[]::new);
    }

    private static void assertNoPublicFields(Class<?> type) {
        Field[] fields = Arrays.stream(type.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()))
                .toArray(Field[]::new);
        assertThat(fields).as("public fields of %s", type.getSimpleName()).isEmpty();
    }

    private static void assertExactPublicMethods(Class<?> type, Set<MethodSpec> expected) {
        Set<MethodSpec> actual = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                actual.add(new MethodSpec(method.getName(), List.of(method.getParameterTypes()),
                        method.getReturnType()));
            }
        }
        assertThat(actual).as("public methods of %s", type.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Full signature of a public method: name, parameter types and return type.
     */
    record MethodSpec(String name, List<Class<?>> parameterTypes, Class<?> returnType) {

        static MethodSpec of(String name, Class<?> returnType, Class<?>... parameterTypes) {
            return new MethodSpec(name, List.of(parameterTypes), returnType);
        }
    }
}
