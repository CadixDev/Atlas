/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.bombe.jar.JarResourceEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

class JarFileTest {

    @TempDir
    static Path workDir;

    private static final AtomicInteger ORIG_COUNTER = new AtomicInteger();
    private static final AtomicInteger OUTPUT_COUNTER = new AtomicInteger();

    @Test
    void testSingleTransformFailure() throws IOException {
        final Path sourceJar = simpleTempJar();
        final Path output = nextOutputFile();

        try (final JarFile jar = new JarFile(sourceJar)) {
            final JarTransformFailedException ex = assertThrows(JarTransformFailedException.class, () -> jar.transform(output, new FailingEntryTransformer("two.properties")));
            assertEquals(1, ex.getFailedPaths().size());
            assertNotNull(ex.getFailedPaths().get(new JarPath("two.properties")));
            assertTrue(ex.getCause() instanceof RuntimeException); // error when single-failure
        }
    }

    @Test
    void testMultiTransformFailure() throws IOException {
        final Path sourceJar = simpleTempJar();
        final Path output = nextOutputFile();

        try (final JarFile jar = new JarFile(sourceJar)) {
            final JarTransformFailedException ex = assertThrows(JarTransformFailedException.class, () -> jar.transform(output, new FailingEntryTransformer("one.properties", "two.properties")));
            assertEquals(2, ex.getFailedPaths().size());
            assertNotNull(ex.getFailedPaths().get(new JarPath("one.properties")));
            assertNotNull(ex.getFailedPaths().get(new JarPath("two.properties")));
            assertNull(ex.getCause()); // null when multiple failures
        }
    }

    @Test
    void testSingleProcessFailure() throws IOException {
        final Path sourceJar = simpleTempJar();

        try (final JarFile jar = new JarFile(sourceJar)) {
            final JarTransformFailedException ex = assertThrows(JarTransformFailedException.class, () -> jar.process(new FailingEntryTransformer("two.properties")));
            assertEquals(1, ex.getFailedPaths().size());
            assertNotNull(ex.getFailedPaths().get(new JarPath("two.properties")));
            assertTrue(ex.getCause() instanceof RuntimeException); // error when single-failure
        }
    }

    @Test
    void testMultiProcessFailure() throws IOException {
        final Path sourceJar = simpleTempJar();
        try (final JarFile jar = new JarFile(sourceJar)) {
            final JarTransformFailedException ex = assertThrows(JarTransformFailedException.class, () -> jar.process(new FailingEntryTransformer("one.properties", "two.properties")));
            assertEquals(2, ex.getFailedPaths().size());
            assertNotNull(ex.getFailedPaths().get(new JarPath("one.properties")));
            assertNotNull(ex.getFailedPaths().get(new JarPath("two.properties")));
            assertNull(ex.getCause()); // null when multiple failures
        }
    }

    /**
     * Make a simple temporary jar with some properties files.
     *
     * @return the temporary jar
     * @throws IOException if failed to create jar
     */
    private static Path simpleTempJar() throws IOException {
        return makeTempJar(
            entry("one.properties", "hello"),
            entry("two.properties", "world"),
            entry("three.properties", "whee")
        );
    }


    /**
     * A transformer that will throw a {@link RuntimeException} on chose
     * entries, for testing error handling.
     */
    private static class FailingEntryTransformer implements JarEntryTransformer {
        private final Set<String> namesToFail;

        FailingEntryTransformer(final String... failingNames) {
            this.namesToFail = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(failingNames)));
        }

        @Override
        public JarClassEntry transform(final JarClassEntry entry) {
            if (this.namesToFail.contains(entry.getName())) {
                throw new RuntimeException("injected failure on " + entry.getName());
            }
            return entry;
        }

        @Override
        public JarResourceEntry transform(final JarResourceEntry entry) {
            if (this.namesToFail.contains(entry.getName())) {
                throw new RuntimeException("injected failure on " + entry.getName());
            }
            return entry;
        }
    }

    // Test file production //

    private static Map.Entry<String, String> entry(final String key, final String value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    // Create a jar in the test directory
    @SafeVarargs
    private static Path makeTempJar(final Map.Entry<String, String>... files) throws IOException {
        final Path test = workDir.resolve("orig" + ORIG_COUNTER.getAndIncrement() + ".jar");
        try (final JarOutputStream os = new JarOutputStream(Files.newOutputStream(test))) {
            for (final Map.Entry<String, String> file : files) {
                os.putNextEntry(new ZipEntry(file.getKey()));

                os.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            }
        }

        return test;
    }

    // Get a unique output file
    private static Path nextOutputFile() {
        return workDir.resolve("output" + OUTPUT_COUNTER.getAndIncrement() + ".jar");
    }

}
