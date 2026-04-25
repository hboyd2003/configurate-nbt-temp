/*
 * configurate-nbt
 * Copyright (c) 2026 Harrison Boyd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.hboyd.configurateNBT;

import dev.hboyd.configurateNBT.serializer.BinaryTagSerializer;
import net.kyori.adventure.builder.AbstractBuilder;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.util.Buildable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.*;
import org.spongepowered.configurate.reference.ConfigurationReference;
import org.spongepowered.configurate.reference.WatchServiceListener;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.util.UnmodifiableCollections;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * TODO: NBTConfigurationLoader JavaDoc.
 */
public final class NBTConfigurationLoader implements ConfigurationLoader<BasicConfigurationNode>, Buildable<NBTConfigurationLoader, NBTConfigurationLoader.Builder> {
    private static final Set<Class<?>> NATIVE_TYPES = UnmodifiableCollections.toSet(
            Integer.class, Double.class, Byte.class, Long.class, Short.class, Float.class, // numeric
            int[].class, byte[].class, long[].class, String.class); // complex types

    private final Callable<BufferedInputStream> source;
    private final Callable<BufferedOutputStream> sink;
    private final ConfigurationOptions defaultOptions;
    private final BinaryTagIO.Compression compressor;

    private NBTConfigurationLoader(Callable<BufferedInputStream> source,
                                   Callable<BufferedOutputStream> sink,
                                   BinaryTagIO.Compression compressor,
                                   ConfigurationOptions defaultOptions) {
        this.source = source;
        this.sink = sink;
        this.compressor = compressor;
        this.defaultOptions = defaultOptions;
    }

    /**
     * Attempts to load a {@link ConfigurationNode} using this loader, from the defined source.
     *
     * <p>The resultant node represents the root of the configuration being
     * loaded.</p>
     *
     * @param options the options to load with
     * @return the newly constructed node
     * @throws ConfigurateException if any sort of error occurs with reading or
     *                              parsing the configuration
     */
    @Override
    public BasicConfigurationNode load(ConfigurationOptions options) throws ConfigurateException {
        BufferedInputStream inputStream;
        try {
            inputStream = source.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return load(options, inputStream);
    }

    /**
     * Attempts to load a {@link ConfigurationNode} using this loader, using a given byte array as the source.
     *
     * <p>The resultant node represents the root of the configuration being
     * loaded.</p>
     *
     * @param input the byte array to load with
     * @return the newly constructed node
     * @throws ConfigurateException if any sort of error occurs with reading or
     *                              parsing the configuration
     */
    public BasicConfigurationNode loadFromBytes(final byte[] input) throws ConfigurateException {
        return loadFromBytes(defaultOptions, input);
    }

    /**
     * Attempts to load a {@link ConfigurationNode} using this loader, using a given byte array as the source.
     *
     * <p>The resultant node represents the root of the configuration being
     * loaded.</p>
     *
     * @param options the options to load with
     * @param input the byte array to load with
     * @return the newly constructed node
     * @throws ConfigurateException if any sort of error occurs with reading or
     *                              parsing the configuration
     */
    public BasicConfigurationNode loadFromBytes(ConfigurationOptions options, final byte[] input) throws ConfigurateException {
        return load(options, new BufferedInputStream(new ByteArrayInputStream(input)));
    }

    private BasicConfigurationNode load(ConfigurationOptions options, BufferedInputStream inputStream) throws ConfigurateException {
        BasicConfigurationNode node = createNode(options);

        try {
            final CompoundBinaryTag tag = BinaryTagIO.unlimitedReader().read(inputStream, compressor);

            final TypeSerializer<CompoundBinaryTag> serializer
                    = requireNonNull(node.options().serializers().get(CompoundBinaryTag.class), "CompoundBinaryTag serializer");
            serializer.serialize(CompoundBinaryTag.class, tag, node);
        } catch (FileNotFoundException | NoSuchFileException _) {
        } catch (IOException e) {
            throw new ConfigurateException(e);
        }

        return node;
    }

    /**
     * Attempts to load data from the defined source into a {@link ConfigurationReference}.
     * The returned reference will not reload automatically.
     *
     * @return the created reference
     * @throws ConfigurateException when an error occurs within the loader
     * @see WatchServiceListener#listenToConfiguration(Function, Path) to
     *      create an auto-reloading configuration.
     */
    @Override
    public ConfigurationReference<BasicConfigurationNode> loadToReference() throws ConfigurateException {
        return ConfigurationReference.fixed(this);
    }


    /**
     * Attempts to save a {@link ConfigurationNode} using this loader, to the defined sink.
     *
     * @param node the node to save
     * @throws ConfigurateException if any sort of error occurs with writing or
     *                     generating the configuration
     */
    @Override
    public void save(ConfigurationNode node) throws ConfigurateException {
        BufferedOutputStream outputStream;
        try {
            outputStream = sink.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        save(node, outputStream, compressor);
    }

    /**
     * Attempts to save a {@link ConfigurationNode} using this loader, to a returned byte array.
     *
     * @param node the node to save
     * @return the saved {@link ConfigurationNode} as a byte array
     * @throws ConfigurateException if any sort of error occurs with writing or
     *                     generating the configuration
     */
    public byte[] saveToBytes(ConfigurationNode node) throws ConfigurateException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        save(node, new BufferedOutputStream(byteArrayOutputStream), this.compressor);
        return byteArrayOutputStream.toByteArray();
    }

    private static void save(ConfigurationNode node, BufferedOutputStream outputStream, BinaryTagIO.Compression compression) throws ConfigurateException {
        final TypeSerializer<CompoundBinaryTag> serializer
                = requireNonNull(node.options().serializers().get(CompoundBinaryTag.class), "CompoundBinaryTag serializer");
        final CompoundBinaryTag tag = serializer.deserialize(CompoundBinaryTag.class, node);

        try {
            BinaryTagIO.writer().write(tag, outputStream, compression);
            outputStream.close();
        } catch (IOException e) {
            throw new ConfigurateException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an empty node with the provided options.
     *
     * @param options node options
     * @return newly created empty node
     */
    @Override
    public BasicConfigurationNode createNode(ConfigurationOptions options) {
        return BasicConfigurationNode.root(options);
    }

    /**
     * Default options for the types of nodes created by this loader.
     *
     * @return default options
     */
    @Override
    public ConfigurationOptions defaultOptions() {
        return this.defaultOptions;
    }

    @Override
    public Builder toBuilder() {
        return new Builder()
                .sink(sink)
                .source(source)
                .compressor(compressor)
                .defaultOptions(defaultOptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements AbstractBuilder<NBTConfigurationLoader>, Buildable.Builder<NBTConfigurationLoader> {
        private ConfigurationOptions defaultOptions;

        private @MonotonicNonNull Callable<BufferedInputStream> source;
        private @MonotonicNonNull Callable<BufferedOutputStream> sink;
        private BinaryTagIO.Compression compressor;

        private Builder() {
            this.defaultOptions = ConfigurationOptions.defaults()
                    .serializers(TypeSerializerCollection.defaults().childBuilder()
                            .registerAll(BinaryTagSerializer.TYPE_SAFE_SERIALIZERS).build())
                    .nativeTypes(NATIVE_TYPES);
            this.compressor = BinaryTagIO.Compression.GZIP;
        }

        /**
         * Sets the sink and source of the resultant loader to the given file.
         *
         * <p>The source is defined using {@link Files#newInputStream(Path, OpenOption...)}</p>
         *
         * <p>The sink is defined using {@link Files#newOutputStream(Path, OpenOption...)}</p>
         *
         * @param file the configuration file
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder file(final File file) {
            return this.path(requireNonNull(file, "file").toPath());
        }

        /**
         * Sets the sink and source of the resultant loader to the given path.
         *
         * @param path the path of the configuration file
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder path(final Path path) {
            final Path absPath = requireNonNull(path, "path").toAbsolutePath();
            this.source = () -> new BufferedInputStream(Files.newInputStream(absPath));
            this.sink = () -> new BufferedOutputStream(Files.newOutputStream(absPath));
            return this;
        }
        
        /**
         * Sets the source of the resultant loader.
         *
         * <p>The "source" is used by the loader to load the configuration.</p>
         *
         * @param source the source
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder source(final Callable<BufferedInputStream> source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the sink of the resultant loader.
         *
         * <p>The "sink" is used by the loader to save the configuration.</p>
         *
         * @param sink the sink
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder sink(final Callable<BufferedOutputStream> sink) {
            this.sink = requireNonNull(sink, "sink");
            return this;
        }

        /**
         * Sets the compression that will be used by the resultant loader.
         *
         * @param compressor type of compression to use
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder compressor(final BinaryTagIO.Compression compressor) {
            this.compressor = requireNonNull(compressor, "compressionType");
            return this;
        }

        /**
         * Sets the default configuration options to be used by the
         * resultant loader.
         *
         * @param defaultOptions the options
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder defaultOptions(final ConfigurationOptions defaultOptions) {
            this.defaultOptions = requireNonNull(defaultOptions, "defaultOptions");
            return this;
        }

        /**
         * Sets the default configuration options to be used by the resultant
         * loader by providing a function which takes the current default
         * options and applies any desired changes.
         *
         * @param defaultOptionsOperator to transform the existing default options
         * @return this builder
         */
        @Contract(value = "_ -> this")
        public Builder defaultOptions(final UnaryOperator<ConfigurationOptions> defaultOptionsOperator) {
            this.defaultOptions = requireNonNull(defaultOptionsOperator.apply(this.defaultOptions), "defaultOptions (updated)");
            return this;
        }

        /**
         * Builds the loader.
         *
         * @return a new loader
         */
        @Override
        @Contract(value = " -> new")
        public NBTConfigurationLoader build() {
            requireNonNull(source, "source");
            requireNonNull(sink, "sink");

            return new NBTConfigurationLoader(source, sink, compressor, defaultOptions);
        }

        /**
         * Configure to read from a byte array, build, and load in one step.
         *
         * @param input the input to load
         * @return a deserialized node
         */
            public ConfigurationNode buildAndLoadByteArray(final byte[] input) throws ConfigurateException {
            return this.source(() -> new BufferedInputStream(new ByteArrayInputStream(input)))
                    .build()
                    .load();
        }

        /**
         * Configure to write to a byte array, build, and save in one step.
         *
         * @param output the node to write
         * @return the output byte array
         */
            public byte[] buildAndSaveByteArray(final ConfigurationNode output) throws ConfigurateException {
            requireNonNull(output, "output");
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            this.sink(() -> new BufferedOutputStream(outputStream))
                    .build()
                    .save(output);
            return outputStream.toByteArray();
        }
    }
}
