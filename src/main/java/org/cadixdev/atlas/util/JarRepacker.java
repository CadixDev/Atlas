/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.util;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public final class JarRepacker {

    private JarRepacker() {
    }

    /**
     * {@link JarInputStream} requires that if a {@code META-INF/MANIFEST.MF} record is present in a jar file, it must be
     * the first entry.
     * <p>
     * In order to maintain compatibility with the jars Atlas produces, this method will check first if
     * the output jar has any manifest file at all, and if it does, if it is retrievable by {@link JarInputStream}.
     * <p>
     * If the output jar does have a manifest file that {@link JarInputStream} can't access, then this method will repack
     * the jar to fix the issue. For performance reasons Atlas remapping process remaps jar entries in parallel, and it
     * uses the NIO zip file system API, which we have no control of. Since this repacking process is a simple copy it is
     * still very fast (compared to the remapping operation).
     *
     * @param outputJar The jar produced by the atlas transformation.
     * @throws IOException If an IO error occurs.
     */
    public static void verifyJarManifest(final Path outputJar) throws IOException {
        final boolean maybeNeedsRepack;
        try (final JarInputStream input = new JarInputStream(Files.newInputStream(outputJar))) {
            maybeNeedsRepack = input.getManifest() == null;
        }
        if (maybeNeedsRepack) {
            final boolean hasManifest;
            try (final JarFile outputJarFile = new JarFile(outputJar.toFile())) {
                hasManifest = outputJarFile.getManifest() != null;
            }
            if (hasManifest) {
                fixJarManifest(outputJar);
            }
        }
    }

    /**
     * Given that the output jar needs to be fixed, repack the given jar with the {@code META-INF/MANIFEST.MF} file as
     * the first entry.
     *
     * @param outputJar The file to repack.
     * @throws IOException If an IO error occurs.
     * @see #verifyJarManifest(Path)
     */
    private static void fixJarManifest(final Path outputJar) throws IOException {
        final byte[] buffer = new byte[8192];

        final Path tempOut = Files.createTempFile(outputJar.getParent(), "atlas", "jar");
        try {
            try (final JarOutputStream out = new JarOutputStream(Files.newOutputStream(tempOut));
                 final JarFile jarFile = new JarFile(outputJar.toFile())) {

                final boolean skipManifest = copyManifest(jarFile, out);

                final Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry currentEntry = entries.nextElement();
                    final String name = currentEntry.getName();
                    if (skipManifest && (name.equals("META-INF/") || name.equalsIgnoreCase("META-INF/MANIFEST.MF"))) {
                        continue;
                    }

                    out.putNextEntry(new ZipEntry(name));
                    try (final InputStream input = jarFile.getInputStream(currentEntry)) {
                        copy(input, out, buffer);
                    }
                    finally {
                        out.closeEntry();
                    }
                }
            }

            Files.move(tempOut, outputJar, REPLACE_EXISTING, ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(tempOut);
        }
    }

    /**
     * Finds the manifest entry in the given {@code jarFile} and copies it into {@code out}.
     *
     * @param jarFile The input file to read the manifest from.
     * @param out The output stream to write the manifest to.
     * @return {@code true} if the manifest file was copied successfully.
     * @throws IOException If an IO error occurs.
     */
    private static boolean copyManifest(final JarFile jarFile, final JarOutputStream out) throws IOException {
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            // something weird happened, but don't error
            return false;
        }

        out.putNextEntry(new ZipEntry("META-INF/"));
        out.closeEntry();

        out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
        manifest.write(out);
        out.closeEntry();

        return true;
    }

    /**
     * Copy all of the data from the {@code from} input to the {@code to} output.
     *
     * @param from The input to copy from.
     * @param to The output to copy to.
     * @param buffer The byte array to use as the copy buffer.
     * @throws IOException If an IO error occurs.
     */
    private static void copy(final InputStream from, final OutputStream to, final byte[] buffer) throws IOException {
        int read;
        while ((read = from.read(buffer)) != -1) {
            to.write(buffer, 0, read);
        }
    }

}
