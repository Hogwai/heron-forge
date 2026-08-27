package dev.hogwai.platform.cli;

import java.util.Locale;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;

/**
 * Validates names used by generated Heron projects and Java sources.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class ProjectNames {

    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private ProjectNames() {
        // utility class
    }

    static String validateProjectName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project name must not be blank");
        }
        if (!PROJECT_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("project name must match [a-z][a-z0-9-]*");
        }
        if (SourceVersion.isKeyword(value)) {
            throw new IllegalArgumentException("project name must not be a Java keyword");
        }
        return value;
    }

    static String validatePackage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("package must not be blank");
        }
        for (String part : value.split("\\.", -1)) {
            validateSegment(part);
        }
        return value;
    }

    private static void validateSegment(String part) {
        if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))
                || part.chars().skip(1).anyMatch(ch -> !Character.isJavaIdentifierPart(ch))) {
            throw new IllegalArgumentException("package must be a valid Java package name");
        }
        if (SourceVersion.isKeyword(part)) {
            throw new IllegalArgumentException("package segment must not be a Java keyword: " + part);
        }
    }

    static String derivePackage(String projectName) {
        String sanitized = projectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return sanitized.isEmpty() ? "app" : validatePackage(sanitized);
    }

    static String toJavaTypeName(String projectName) {
        StringBuilder result = new StringBuilder();
        for (String part : projectName.split("-", -1)) {
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
