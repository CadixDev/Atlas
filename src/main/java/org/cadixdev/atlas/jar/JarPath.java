/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.jar;

import java.util.Objects;

/**
 * A path to an entry within a {@link JarFile JAR file}.
 *
 * @author Jamie Mansfield
 * @since 0.2.0
 */
public class JarPath {

    private final String name;

    public JarPath(final String name) {
        this.name = name;
    }

    /**
     * Gets the path's fully-qualified name.
     *
     * @return The name
     */
    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JarPath)) return false;
        final JarPath that = (JarPath) obj;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

}
