/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas;

import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.jar.JarEntryTransformer;

/**
 * The context used for initialising {@link JarEntryTransformer transformers},
 * so they can access JAR-specific constructs.
 *
 * <p><strong>Only one context will be created per Atlas run.</strong>
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public class AtlasTransformerContext {

    private final InheritanceProvider inheritanceProvider;

    AtlasTransformerContext(final InheritanceProvider inheritanceProvider) {
        this.inheritanceProvider = inheritanceProvider;
    }

    /**
     * Gets the {@link InheritanceProvider inheritance provider} for the JAR
     * Atlas is processing.
     *
     * @return The inheritance provider
     */
    public InheritanceProvider inheritanceProvider() {
        return this.inheritanceProvider;
    }

}
