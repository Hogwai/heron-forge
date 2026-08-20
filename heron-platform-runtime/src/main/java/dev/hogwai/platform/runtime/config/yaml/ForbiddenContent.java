package dev.hogwai.platform.runtime.config.yaml;

import java.util.regex.Pattern;

/**
 * Detects the single remaining forbidden scalar form at the parse boundary.
 *
 * <p>Only environment-style interpolation ({@code ${...}}) is rejected. The
 * former multicloud secret-shaped detection (AWS/GitHub/OpenAI/Slack/Stripe/
 * JWT/private keys and the like) was removed because it was incomplete and
 * produced false positives. {@code include} directives and secret values are
 * intentionally <em>not</em> resolved or rejected here; they are treated as
 * plain scalar content.
 */
final class ForbiddenContent {

    private static final Pattern ENV_INTERPOLATION = Pattern.compile("\\$\\{[^}]*}");

    private ForbiddenContent() {
        // no instances
    }

    /**
     * Returns whether the given value contains a forbidden {@code ${...}}
     * interpolation expression.
     *
     * @param value the value to inspect
     * @return {@code true} if the value contains an interpolation expression
     */
    static boolean hasInterpolation(String value) {
        return value != null && ENV_INTERPOLATION.matcher(value).find();
    }
}
