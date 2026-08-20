package dev.hogwai.platform.spi;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable provider version in canonical {@code major.minor.patch} form.
 *
 * <p>Each component is a non-negative integer without leading zeros. The only
 * accepted textual form is the canonical {@code major.minor.patch} string; no
 * SemVer ranges, pre-release or build metadata are supported. {@link #toString()}
 * always returns the canonical form, so {@code parse(version.toString())} is the
 * identity for any valid version.
 *
 * @param major the major component
 * @param minor the minor component
 * @param patch the patch component
 */
public record ProviderVersion(int major, int minor, int patch) {

    @SuppressWarnings("java:S6353")
    private static final Pattern CANONICAL =
            Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    /**
     * Compact constructor enforcing non-negative components.
     *
     * @throws IllegalArgumentException if any component is negative
     */
    public ProviderVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must be non-negative");
        }
    }

    /**
     * Parses a canonical {@code major.minor.patch} string.
     *
     * <p>Only canonical strings are accepted; non-canonical inputs (missing or
     * extra components, leading zeros, pre-release/build suffixes, whitespace)
     * are rejected.
     *
     * @param version the canonical version string
     * @return the parsed version
     * @throws NullPointerException     if {@code version} is {@code null}
     * @throws IllegalArgumentException if {@code version} is not canonical
     */
    public static ProviderVersion parse(String version) {
        Objects.requireNonNull(version, "version must not be null");
        Matcher matcher = CANONICAL.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("version must be canonical major.minor.patch");
        }
        return new ProviderVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
