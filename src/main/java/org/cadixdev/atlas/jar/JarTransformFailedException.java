/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.jar;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An exception thrown when one or more entries in a jar fail to transform.
 *
 * @since 0.3.0
 */
public final class JarTransformFailedException extends IOException {

    private static final long serialVersionUID = 3971759325502243118L;

    private final Map<JarPath, Exception> failedPaths;

    /**
     * Create a new exception.
     *
     * @param message the user-visible message
     * @param failedPaths a map from failed entry to
     */
    public JarTransformFailedException(final String message, final Map<JarPath, Exception> failedPaths) {
        super(message);
        this.failedPaths = Collections.unmodifiableMap(new HashMap<>(failedPaths));
        if (this.failedPaths.size() == 1) {
            this.initCause(this.failedPaths.values().iterator().next());
        }
    }

    /**
     * Return an unmodifiable snapshot of a map of jar path to the exception
     * thrown at the path.
     *
     * @return the failed paths
     */
    public Map<JarPath, Exception> getFailedPaths() {
        return this.failedPaths;
    }

    @Override
    public String getMessage() {
        final String superMessage = super.getMessage();
        if (this.failedPaths.size() == 1) { // details already included in cause
            return superMessage + " (in entry " + this.failedPaths.keySet().iterator().next().getName() + " )";
        }

        final StringBuilder message = new StringBuilder(superMessage == null ? "Failed to transform jar: " : superMessage);
        for (final Map.Entry<JarPath, Exception> failure : this.failedPaths.entrySet()) {
            message.append(System.lineSeparator())
                .append("- ")
                .append(failure.getKey().getName());
            final String elementMessage = failure.getValue().getMessage();
            if (elementMessage != null) {
                message.append(": ")
                    .append(elementMessage);
            }
        }
        return message.toString();
    }
}
