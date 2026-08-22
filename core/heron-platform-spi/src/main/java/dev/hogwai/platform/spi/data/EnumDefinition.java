package dev.hogwai.platform.spi.data;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of an enumerated type: an identifier and an ordered,
 * unique immutable list representing the set of allowed symbols.
 *
 * <p>The symbols are stored as an ordered list (not a {@code Set}) so that the
 * observable order is preserved in iteration and equality; duplicates are
 * rejected at construction. Blank symbols are rejected. Framework-independent
 * and immutable.
 *
 * @param identifier the non-blank enum identifier
 * @param symbols    the ordered, unique immutable list representing the set of
 *                   allowed symbols
 */
public record EnumDefinition(String identifier, List<String> symbols) {

    /**
     * Compact constructor enforcing the enum definition contract.
     *
     * @throws NullPointerException     if {@code identifier} or any symbol is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code identifier} or any symbol is
     *                                  blank, or if a symbol is duplicated
     */
    public EnumDefinition {
        Objects.requireNonNull(identifier, "identifier must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        Objects.requireNonNull(symbols, "symbols must not be null");
        Set<String> seen = new HashSet<>();
        for (String symbol : symbols) {
            Objects.requireNonNull(symbol, "symbol must not be null");
            if (symbol.isBlank()) {
                throw new IllegalArgumentException("symbol must not be blank");
            }
            if (!seen.add(symbol)) {
                throw new IllegalArgumentException("duplicate symbol: " + symbol);
            }
        }
        symbols = List.copyOf(symbols);
    }
}
