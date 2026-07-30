/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.configuration.configurer;

import com.dre.brewery.configuration.annotation.CommentSpace;
import com.dre.brewery.configuration.annotation.DefaultCommentSpace;
import com.dre.brewery.configuration.annotation.Footer;
import com.dre.brewery.configuration.annotation.LocalizedComment;
import com.dre.brewery.configuration.serdes.BreweryXSerdesPack;
import eu.okaeri.configs.postprocessor.ConfigLineInfo;
import eu.okaeri.configs.postprocessor.ConfigPostprocessor;
import eu.okaeri.configs.postprocessor.format.YamlSectionWalker;
import eu.okaeri.configs.schema.ConfigDeclaration;
import eu.okaeri.configs.schema.FieldDeclaration;
import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.SerdesContext;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import lombok.NonNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class BreweryXConfigurer extends YamlSnakeYamlConfigurer {

    private final Yaml yaml;
    private Map<String, Object> map = new LinkedHashMap<>();
    private String commentPrefix = "# ";

    {
        this.register(new BreweryXSerdesPack());
    }

    public BreweryXConfigurer(@NonNull final Yaml yaml, @NonNull final Map<String, Object> map) {
        this.yaml = yaml;
        this.map = map;
    }

    public BreweryXConfigurer(@NonNull final Yaml yaml) {
        this.yaml = yaml;
    }

    public BreweryXConfigurer() {
        this(createYaml());
    }

    private static Yaml createYaml() {

        final var loaderOptions = new LoaderOptions();
        final var constructor = new Constructor(loaderOptions);

        final var dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setWidth(80);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        final var representer = new Representer(dumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        final var resolver = new Resolver();

        return new Yaml(constructor, representer, dumperOptions, loaderOptions, resolver);
    }

    private static <T> T apply(final T object, final Consumer<T> consumer) {
        consumer.accept(object);
        return object;
    }

    @Override
    public final List<String> getExtensions() {
        return Arrays.asList("yml", "yaml");
    }

    @Override
    public final void setValue(@NonNull final String key, final Object value, final GenericsDeclaration type, final FieldDeclaration field) {
        final var simplified = this.simplify(value, type, SerdesContext.of(this, field), true);
        this.map.put(key, simplified);
    }

    @Override
    public final void setValueUnsafe(@NonNull final String key, final Object value) {
        this.map.put(key, value);
    }

    @Override
    public final Object getValue(@NonNull final String key) {
        return this.map.get(key);
    }

    @Override
    public final Object remove(@NonNull final String key) {
        return this.map.remove(key);
    }

    @Override
    public final boolean keyExists(@NonNull final String key) {
        return this.map.containsKey(key);
    }

    @Override
    public final List<String> getAllKeys() {
        return List.copyOf(this.map.keySet());
    }

    @Override
    public final void load(@NonNull final InputStream inputStream, @NonNull final ConfigDeclaration declaration) throws Exception {
        // try loading from input stream
        this.map = this.yaml.load(inputStream);
        // when no map was loaded reset with empty
        if (this.map == null) this.map = new LinkedHashMap<>();
    }

    @Override
    public final void write(@NonNull final OutputStream outputStream, @NonNull final ConfigDeclaration declaration) throws Exception {

        final Map<String, Object> mapCopy = new LinkedHashMap<>(this.map); // Not sure if I should copy or do this on the main map
        // Remove null values
        this.removeNullValues(mapCopy);
        // render to string
        final var contents = this.yaml.dump(mapCopy);

        final var footer = declaration.getType().getAnnotation(Footer.class);
        final var footerLines = footer != null ? footer.value() : new String[0];
        // postprocess
        ConfigPostprocessor.of(contents)
                // remove all current top-level comments
                .removeLines((line) -> line.startsWith(this.commentPrefix.trim()))
                // add new comments
                .updateLinesKeys(new YamlSectionWalker() {
                    @Override
                    public String update(final String line, final ConfigLineInfo lineInfo, final List<ConfigLineInfo> path) {

                        var currentDeclaration = declaration;
                        for (var i = 0; i < (path.size() - 1); i++) {
                            final var pathElement = path.get(i);
                            final var field = currentDeclaration.getField(pathElement.getName());
                            if (field.isEmpty()) {
                                return line;
                            }
                            final var fieldType = field.get().getType();
                            if (!fieldType.isConfig()) {
                                return line;
                            }
                            currentDeclaration = ConfigDeclaration.of(fieldType.getType());
                        }

                        final var lineDeclaration = currentDeclaration.getField(lineInfo.getName());
                        if (lineDeclaration.isEmpty()) {
                            return line;
                        }

                        // Localized comments
                        final var fieldComment = lineDeclaration.get().getComment(); // regular okaeri comments
                        final var localizedComment = BreweryXConfigurer.this.getFieldComments(lineDeclaration.get()); // localized ones

                        // Joins 2 nullable arrays
                        final var finalComment = Stream.of(fieldComment, localizedComment)
                                .filter(Objects::nonNull) // omit if null
                                .flatMap(Arrays::stream)
                                .toArray(String[]::new);

                        if (finalComment.length == 0)
                            return line;


                        final var comment = new StringBuilder();

                        var space = declaration.getType().isAnnotationPresent(DefaultCommentSpace.class) ? declaration.getType().getAnnotation(DefaultCommentSpace.class).value() : 0;
                        if (lineDeclaration.get().getField().isAnnotationPresent(CommentSpace.class)) {
                            space = lineDeclaration.get().getField().getAnnotation(CommentSpace.class).value();
                        }

                        if (space > 0) {
                            comment.repeat("\n", space);
                        }

                        comment.append(ConfigPostprocessor.createComment(BreweryXConfigurer.this.commentPrefix, finalComment));
                        return ConfigPostprocessor.addIndent(comment.toString(), lineInfo.getIndent()) + line;
                    }
                })
                // add header if available
                .prependContextComment(this.commentPrefix, declaration.getHeader())
                // add footer if available
                .appendContextComment(this.commentPrefix, footerLines)
                // save
                .write(outputStream);
    }

    @Override
    public final YamlSnakeYamlConfigurer setCommentPrefix(final String commentPrefix) {
        this.commentPrefix = commentPrefix;
        return this;
    }

    /**
     * Processes the {{@link TranslationManager}} annotation
     * Doesn't throw when there is no translation!
     */
    public final String[] getFieldComments(final FieldDeclaration fieldDeclaration) {
        final var localizedComment = fieldDeclaration.getField().getAnnotation(LocalizedComment.class);
        if (localizedComment == null || localizedComment.value().length == 0)
            return null;

        final var translationManager = TranslationManager.getInstance();

        return Arrays.stream(localizedComment.value())
                .map(translationManager::getTranslationWithFallback)
                .filter(Objects::nonNull) // Remove null translations
                .flatMap(translation -> Arrays.stream(translation.split("\n"))) // Split translations by lines
                .toArray(String[]::new);
    }


    // Probably could rewrite this null removal to be more efficient...

    public final void removeNullValues(final Map<String, Object> map) {
        if (map == null) {
            return;
        }

        // Iterate over the map and remove null values
        final var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final var value = entry.getValue();

            switch (value) {
                case null -> iterator.remove(); // Remove entry if value is null
                case Map map1 -> {
                    // If the value is a map, recursively remove null values inside it
                    this.removeNullValues((Map<String, Object>) value);
                    if (((Map<String, Object>) value).isEmpty()) {
                        iterator.remove(); // Remove the map if it becomes empty after cleaning
                    }
                }
                case Collection collection -> {
                    // If the value is a collection, remove null values from it
                    this.removeNullValuesFromCollection(collection);
                    if (collection.isEmpty()) {
                        iterator.remove(); // Remove the collection if it becomes empty after cleaning
                    }

                    // If the collection is a List, check if it's a list of maps and clean the maps
                    if (value instanceof List) {
                        this.removeNullValuesFromListOfMaps((List<?>) value);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void removeNullValuesFromCollection(final Collection<?> collection) {
        if (collection == null) {
            return;
        }

        // Remove null values from the collection (List, Set, etc.)
        collection.removeIf(Objects::isNull);
    }

    private void removeNullValuesFromListOfMaps(final List<?> list) {
        if (list == null) {
            return;
        }

        // Iterate over the list and clean each map
        final var iterator = list.iterator();
        while (iterator.hasNext()) {
            final var element = iterator.next();
            if (element instanceof Map) {
                // Recursively clean the map inside the list
                this.removeNullValues((Map<String, Object>) element);
                if (((Map<String, Object>) element).isEmpty()) {
                    iterator.remove(); // Remove the map if it becomes empty after cleaning
                }
            }
        }
    }
}
