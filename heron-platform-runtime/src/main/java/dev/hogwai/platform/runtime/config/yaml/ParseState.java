package dev.hogwai.platform.runtime.config.yaml;


/**
 * Mutable state tracked while walking the YAML token stream.
 *
 * <p>Package-private helper shared by the tree builder and value reader.
 */
final class ParseState {

    final YamlLimits limits;
    int depth;
    int nodeCount;
    boolean failed;

    ParseState(YamlLimits limits) {
        this.limits = limits;
    }
}
