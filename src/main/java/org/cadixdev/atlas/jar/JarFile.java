/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas.jar;

import org.cadixdev.atlas.util.NIOHelper;
import org.cadixdev.bombe.jar.AbstractJarEntry;
import org.cadixdev.bombe.provider.ClassProvider;
import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.cadixdev.bombe.jar.JarManifestEntry;
import org.cadixdev.bombe.jar.JarResourceEntry;
import org.cadixdev.bombe.jar.JarServiceProviderConfigurationEntry;
import org.cadixdev.bombe.jar.ServiceProviderConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * A representation of a JAR file, with the class entries cached.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public class JarFile implements ClassProvider, Closeable {

    private final Path path;
    private final FileSystem fs;
    private final Map<JarPath, JarClassEntry> cache = new ConcurrentHashMap<>();

    public JarFile(final Path path) throws IOException {
        this.path = path;
        this.fs = NIOHelper.openZip(this.path, false);
    }

    /**
     * Gets the name (location on file system) of the JAR file.
     *
     * @return The name
     */
    public String getName() {
        return this.path.toString();
    }

    /**
     * Gets the entry for the given {@link JarPath JAR path}.
     * <p>
     * {@link JarClassEntry Class entries} will be cached upon use.
     *
     * @param path The path of the entry
     * @return The entry, or {@code null} if no entry for the path exists
     * @throws IOException Should an issue occur reading the entry
     */
    public AbstractJarEntry get(final JarPath path) throws IOException {
        final Path entry = this.fs.getPath("/", path.getName());
        if (Files.notExists(entry)) return null;

        if ("META-INF/MANIFEST.MF".equals(path.getName())) {
            return _readManifest(entry);
        }
        else if (path.getName().startsWith("META-INF/services/")) {
            return _readServiceConfig(entry);
        }
        else if (path.getName().endsWith(".class")) {
            return this.getClass(path);
        }
        else {
            return _readResource(entry);
        }
    }

    /**
     * Gets the <strong>cached</strong> class entry, of the given
     * {@link JarPath JAR path}.
     *
     * @param path The class's JAR entry path
     * @return The class entry, or {@code null} if not present
     */
    public JarClassEntry getClass(final JarPath path) {
        return this.cache.computeIfAbsent(path, p -> {
            final Path entry = this.fs.getPath("/", p.getName());
            if (Files.notExists(entry)) return null;
            try {
                return _readClass(entry);
            }
            catch (final IOException ignored) {
                return null;
            }
        });
    }

    /**
     * Gets the <strong>cached</strong> class entry, of the given name.
     *
     * @param name The class name
     * @return The class entry, or {@code null} if not present
     */
    public JarClassEntry getClass(final String name) {
        return this.getClass(new JarPath(name));
    }

    /**
     * Walks through the jar entries within the JAR file, omitting those targeted
     * by a {@link JarVisitOption}.
     *
     * @param options The visit options to use, while walking
     * @return The jar entries
     * @throws IOException Should an issue with reading occur
     */
    public Stream<JarPath> walk(final JarVisitOption... options) throws IOException {
        return Files.walk(this.fs.getPath("/")).filter(p -> !Files.isDirectory(p)).map(p -> {
            final String name = p.toString().substring(1); // Trim leading /

            if ("META-INF/MANIFEST.MF".equals(name)) {
                if (_contains(JarVisitOption.IGNORE_MANIFESTS, options)) return null;
            }
            else if (name.startsWith("META-INF/services/")) {
                if (_contains(JarVisitOption.IGNORE_SERVICE_PROVIDER_CONFIGURATIONS, options)) return null;
            }
            else if (name.endsWith(".class")) {
                if (_contains(JarVisitOption.IGNORE_CLASSES, options)) return null;
            }
            else {
                if (_contains(JarVisitOption.IGNORE_RESOURCES, options)) return null;
            }

            return new JarPath(name);
        }).filter(Objects::nonNull);
    }

    /**
     * Transforms the JAR file, with the given {@link JarEntryTransformer}s, writing
     * to the given output JAR path.
     * <p>
     * This will use {@link Executors#newWorkStealingPool()} as the executor service,
     * use {@link #transform(Path, ExecutorService, JarEntryTransformer...)} if you
     * wish to control this.
     *
     * @param export The JAR path to write to
     * @param transformers The transformers to use
     * @throws IOException Should an issue with reading or writing occur
     * @throws JarTransformFailedException if one or more transformers threw
     *     exceptions while processing a jar entry
     */
    public void transform(final Path export, final JarEntryTransformer... transformers) throws IOException {
        final ExecutorService executorService = Executors.newWorkStealingPool();
        try {
            this.transform(export, executorService, transformers);
        }
        finally {
            executorService.shutdown();
        }
    }

    /**
     * Transforms the JAR file, with the given {@link JarEntryTransformer}s, writing
     * to the given output JAR path.
     *
     * @param export The JAR path to write to
     * @param executorService The executor service to use
     * @param transformers The transformers to use
     * @throws IOException Should an issue with reading or writing occur
     * @throws JarTransformFailedException if one or more transformers threw
     *     exceptions while processing a jar entry
     * @since 0.2.1
     */
    public void transform(final Path export, final ExecutorService executorService, final JarEntryTransformer... transformers) throws IOException {
        Files.deleteIfExists(export);
        final Map<JarPath, Exception> failedLocations = new ConcurrentHashMap<>();
        try (final FileSystem fs = NIOHelper.openZip(export, true)) {
            final CompletableFuture<Void> future = CompletableFuture.allOf(this.walk().map(path -> CompletableFuture.runAsync(() -> {
                try {
                    // Get the entry
                    AbstractJarEntry entry = this.get(path);
                    if (entry == null) return;

                    // Transform the entry
                    for (final JarEntryTransformer transformer : transformers) {
                        entry = entry.accept(transformer);
                        if (entry == null) return;
                    }

                    // Write to jar
                    final Path outEntry = fs.getPath("/", entry.getName());

                    // Ensure parent directory exists
                    Files.createDirectories(outEntry.getParent());

                    // Write the result to the new jar
                    Files.write(outEntry, entry.getContents());
                    Files.setLastModifiedTime(outEntry, FileTime.fromMillis(entry.getTime()));
                }
                catch (final Exception ex) {
                    failedLocations.put(path, ex);
                }
            }, executorService)).toArray(CompletableFuture[]::new));

            future.handle((value, error) -> {
                // Capture errors
                if (error != null) {
                    throw new RuntimeException(error);
                }
                return value;
            }).get();

            if (!failedLocations.isEmpty()) {
                // Some entries failed to transform
                throw new JarTransformFailedException("Some entries in " + this.getName() + " failed to be transformed", failedLocations);
            }

            // Add additions from transformers
            for (final JarEntryTransformer transformer : transformers) {
                for (final AbstractJarEntry addition : transformer.additions()) {
                    // Write to jar
                    final Path outEntry = fs.getPath("/", addition.getName());

                    // Ensure parent directory exists
                    Files.createDirectories(outEntry.getParent());

                    // Write the result to the new jar
                    Files.write(outEntry, addition.getContents());
                    Files.setLastModifiedTime(outEntry, FileTime.fromMillis(addition.getTime()));
                }
            }
        }
        catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        catch (final ExecutionException ex) {
            try {
                throw ex.getCause();
            }
            catch (final IOException ioe) {
                throw ioe;
            }
            catch (final Throwable cause) {
                throw new RuntimeException(cause);
            }
        }
    }

    /**
     * Processes the JAR file, running the given {@link JarEntryTransformer jar entry transformers}
     * for each path within the jar.
     * <p>
     * The eventual result of transformation is ignored, and not written to file.
     * {@link #transform(Path, ExecutorService, JarEntryTransformer...)} should be used if such
     * behaviour is desired.
     * <p>
     * This will use {@link Executors#newWorkStealingPool()} as the executor service, use
     * {@link #process(ExecutorService, JarEntryTransformer...)} if you wish to control this.
     *
     * @param transformers The transformers to use
     * @throws IOException Should an issue with reading occur
     * @throws JarTransformFailedException if one or more transformers threw
     *     exceptions while processing a jar entry
     * @since 0.2.2
     */
    public void process(final JarEntryTransformer... transformers) throws IOException {
        final ExecutorService executorService = Executors.newWorkStealingPool();
        try {
            this.process(executorService, transformers);
        }
        finally {
            executorService.shutdown();
        }
    }

    /**
     * Processes the JAR file, running the given {@link JarEntryTransformer jar entry transformers}
     * for each path within the jar.
     * <p>
     * The eventual result of transformation is ignored, and not written to file.
     * {@link #transform(Path, ExecutorService, JarEntryTransformer...)} should be used if such
     * behaviour is desired.
     *
     * @param executorService The executor service to use
     * @param transformers The transformers to use
     * @throws IOException Should an issue with reading occur
     * @throws JarTransformFailedException if one or more transformers threw
     *     exceptions while processing a jar entry
     * @since 0.2.2
     */
    public void process(final ExecutorService executorService, final JarEntryTransformer... transformers)
            throws IOException {
        final Map<JarPath, Exception> failedLocations = new ConcurrentHashMap<>();
        final CompletableFuture<Void> future = CompletableFuture.allOf(this.walk().map(path -> CompletableFuture.runAsync(() -> {
            try {
                // Get the entry
                AbstractJarEntry entry = this.get(path);
                if (entry == null) return;

                // Transform the entry
                for (final JarEntryTransformer transformer : transformers) {
                    entry = entry.accept(transformer);
                    if (entry == null) return;
                }
            }
            catch (final Exception ex) {
                failedLocations.put(path, ex);
            }
        }, executorService)).toArray(CompletableFuture[]::new));

        try {
            future.handle((value, error) -> {
                // Capture errors
                if (error != null) {
                    throw new RuntimeException(error);
               }
                return value;
            }).get();

            if (!failedLocations.isEmpty()) {
                // Some entries failed to transform
                throw new JarTransformFailedException("Some entries in " + this.getName() + " failed to be transformed", failedLocations);
            }
        }
        catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        catch (final ExecutionException ex) {
            try {
                throw ex.getCause();
            }
            catch (final IOException ioe) {
                throw ioe;
            }
            catch (final Throwable cause) {
                throw new RuntimeException(cause);
            }
        }
    }

    @Override
    public byte[] get(final String klass) {
        final JarClassEntry entry = this.getClass(klass + ".class");
        return entry == null ? null : entry.getContents();
    }

    @Override
    public void close() throws IOException {
        this.fs.close();
    }

    private static JarManifestEntry _readManifest(final Path entry) throws IOException {
        final long time = Files.getLastModifiedTime(entry).toMillis();

        try (final InputStream is = Files.newInputStream(entry)) {
            return new JarManifestEntry(time, new Manifest(is));
        }
    }

    private static JarServiceProviderConfigurationEntry _readServiceConfig(final Path entry) throws IOException {
        final String name = entry.toString().substring(1); // Remove '/' prefix
        final long time = Files.getLastModifiedTime(entry).toMillis();

        try (final InputStream is = Files.newInputStream(entry)) {
            final String serviceName = name.substring("META-INF/services/".length());

            final ServiceProviderConfiguration config = new ServiceProviderConfiguration(serviceName);
            config.read(is);
            return new JarServiceProviderConfigurationEntry(time, config);
        }
    }

    private static JarClassEntry _readClass(final Path entry) throws IOException {
        final String name = entry.toString().substring(1); // Remove '/' prefix
        final long time = Files.getLastModifiedTime(entry).toMillis();
        return new JarClassEntry(name, time, Files.readAllBytes(entry));
    }

    private static JarResourceEntry _readResource(final Path entry) throws IOException {
        final String name = entry.toString().substring(1); // Remove '/' prefix
        final long time = Files.getLastModifiedTime(entry).toMillis();
        return new JarResourceEntry(name, time, Files.readAllBytes(entry));
    }

    private static <T> boolean _contains(final T entry, final T[] in) {
        for (final T i : in) {
            if (i == entry) return true;
        }
        return false;
    }

}
