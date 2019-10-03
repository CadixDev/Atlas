/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper class for interacting with Java's NIO.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public final class NIOHelper {

    /**
     * Opens the specified zip file, optionally creating it if it doesn't
     * exist.
     *
     * @param path The path to the zip file
     * @param create Whether to create the file, should it not exist
     * @return The file system for the zip file
     * @throws IOException Should an issue occur while opening the zip file
     */
    public static FileSystem openZip(final Path path, final boolean create) throws IOException {
        final URI uri = URI.create("jar:" + path.toUri());
        final Map<String, String> options = new HashMap<>();
        options.put("create", Boolean.toString(create));
        return FileSystems.newFileSystem(uri, options);
    }

    private NIOHelper() {
    }

}
