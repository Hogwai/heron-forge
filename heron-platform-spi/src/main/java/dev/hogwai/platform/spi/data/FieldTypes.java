package dev.hogwai.platform.spi.data;

/**
 * Package-private type compatibility helper for {@link SchemaCompatibility}.
 *
 * <p>Kept separate from {@link SchemaCompatibility} so that the public class
 * stays within the project's cyclomatic complexity budget while the type
 * compatibility logic remains within the data package.
 */
final class FieldTypes {

    private FieldTypes() {
    }

    /**
     * Returns whether an output field type can safely feed an input field type
     * without implicit numeric widening.
     *
     * @param output the producing field type
     * @param input  the consuming field type
     * @return {@code true} if the types are compatible
     */
    static boolean compatible(FieldType output, FieldType input) {
        if (output instanceof FieldType.StringType && input instanceof FieldType.StringType) {
            return true;
        }
        if (output instanceof FieldType.BooleanType && input instanceof FieldType.BooleanType) {
            return true;
        }
        if (output instanceof FieldType.Int64Type && input instanceof FieldType.Int64Type) {
            return true;
        }
        if (output instanceof FieldType.DecimalType && input instanceof FieldType.DecimalType) {
            return true;
        }
        if (output instanceof FieldType.InstantType && input instanceof FieldType.InstantType) {
            return true;
        }
        if (output instanceof FieldType.EnumType(EnumDefinition outputDefinition)
                && input instanceof FieldType.EnumType(EnumDefinition inputDefinition)) {
            return outputDefinition.identifier().equals(inputDefinition.identifier())
                    && inputDefinition.symbols().containsAll(outputDefinition.symbols());
        }
        return false;
    }
}
