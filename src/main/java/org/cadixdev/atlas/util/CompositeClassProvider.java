/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.util;

import org.cadixdev.bombe.provider.ClassProvider;

import java.util.List;

/**
 * A {@link ClassProvider class provider} backed by many other class providers.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public class CompositeClassProvider implements ClassProvider {

    private final List<ClassProvider> providers;

    public CompositeClassProvider(final List<ClassProvider> providers) {
        this.providers = providers;
    }

    @Override
    public byte[] get(final String klass) {
        for (final ClassProvider provider : this.providers) {
            final byte[] raw = provider.get(klass);
            if (raw != null) return raw;
        }
        return null;
    }

}
