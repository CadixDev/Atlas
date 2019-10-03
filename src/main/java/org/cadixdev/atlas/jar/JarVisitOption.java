/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.jar;

/**
 * Options for visiting entries in a jar file.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public enum JarVisitOption {

    /**
     * Will avoid reading manifests in the Jar.
     */
    IGNORE_MANIFESTS,

    /**
     * Will avoid reading service provider configurations in
     * the Jar.
     */
    IGNORE_SERVICE_PROVIDER_CONFIGURATIONS,

    /**
     * Will avoid reading classes in the Jar.
     */
    IGNORE_CLASSES,

    /**
     * Will avoid reading resources in the Jar.
     */
    IGNORE_RESOURCES,
    ;

}
