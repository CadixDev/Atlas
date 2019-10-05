/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * Atlas' representation of a JAR file, allowing for the
 * {@link org.cadixdev.bombe.jar.AbstractJarEntry jar entries} to be read
 * and {@link org.cadixdev.bombe.jar.JarEntryTransformer transformed}.
 *
 * <p>{@link org.cadixdev.bombe.jar.JarClassEntry Class entries} will be
 * cached, to avoid making lots of IO calls.
 */
package org.cadixdev.atlas.jar;
