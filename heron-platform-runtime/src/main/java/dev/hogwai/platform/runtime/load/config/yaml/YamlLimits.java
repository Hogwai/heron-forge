package dev.hogwai.platform.runtime.load.config.yaml;

/**
 * Bounds applied while parsing a configuration document.
 *
 * <p>All limits are strictly positive and immutable. They protect the parser
 * against oversized, deeply nested or excessively large documents.
 *
 * @param maxBytes        the maximum number of bytes read from the input
 * @param maxDepth        the maximum nesting depth of mappings and sequences
 * @param maxNodes        the maximum number of nodes in the document
 * @param maxStringLength the maximum length of a single string value
 */
public record YamlLimits(int maxBytes, int maxDepth, int maxNodes, int maxStringLength) {

    /**
     * Compact constructor enforcing strictly positive limits.
     *
     * @throws IllegalArgumentException if any limit is not strictly positive
     */
    public YamlLimits {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be strictly positive");
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be strictly positive");
        }
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be strictly positive");
        }
        if (maxStringLength <= 0) {
            throw new IllegalArgumentException("maxStringLength must be strictly positive");
        }
    }

    /**
     * Returns the default limits.
     *
     * @return the default limits
     */
    public static YamlLimits defaults() {
        return new YamlLimits(64 * 1024, 20, 10_000, 4096);
    }
}