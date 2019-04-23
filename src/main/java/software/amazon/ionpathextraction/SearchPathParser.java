/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package software.amazon.ionpathextraction;

import static software.amazon.ionpathextraction.internal.Preconditions.checkArgument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonType;
import software.amazon.ion.IonWriter;
import software.amazon.ion.system.IonReaderBuilder;
import software.amazon.ion.system.IonTextWriterBuilder;
import software.amazon.ionpathextraction.exceptions.PathExtractionException;
import software.amazon.ionpathextraction.pathcomponents.AnnotatedPathComponent;
import software.amazon.ionpathextraction.pathcomponents.Index;
import software.amazon.ionpathextraction.pathcomponents.PathComponent;
import software.amazon.ionpathextraction.pathcomponents.Text;
import software.amazon.ionpathextraction.pathcomponents.Wildcard;

/**
 * Parses a search path ion expression into a {@link SearchPath}s.
 */
final class SearchPathParser {

    private static final IonReaderBuilder READER_BUILDER = IonReaderBuilder.standard();
    private static final IonTextWriterBuilder WRITER_BUILDER = IonTextWriterBuilder.standard();

    private static final String WILDCARD_ESCAPE_ANNOTATION = "$ion_extractor_field";

    // only has static methods, should not be invoked
    private SearchPathParser() {
    }

    static <T> SearchPath<T> parse(final String ionPathExpression, final BiFunction<IonReader, T, Integer> callback) {
        final List<PathComponent> pathComponents;

        try (final IonReader reader = newIonReader(ionPathExpression)) {
            checkArgument(reader.next() != null, "ionPathExpression cannot be empty");
            checkArgument(reader.getType() == IonType.SEXP || reader.getType() == IonType.LIST,
                "ionPathExpression must be a s-expression or list");

            final String[] typeAnnotations = reader.getTypeAnnotations();

            reader.stepIn();
            pathComponents = parsePathComponents(reader);
            reader.stepOut();

            return new SearchPath<>(pathComponents, callback, typeAnnotations);
        } catch (IOException e) {
            throw new PathExtractionException(e);
        }
    }

    private static List<PathComponent> parsePathComponents(final IonReader reader) {
        final List<PathComponent> pathComponents = new ArrayList<>();

        while (reader.next() != null) {
            pathComponents.add(readComponent(reader));
        }

        return pathComponents;
    }

    private static PathComponent readComponent(final IonReader reader) {
        final PathComponent pathComponent;
        switch (reader.getType()) {
            case INT:
                pathComponent = new Index(reader.intValue());
                break;

            case STRING:
            case SYMBOL:
                if (isWildcard(reader)) {
                    pathComponent = Wildcard.INSTANCE;
                } else {
                    pathComponent = new Text(reader.stringValue());
                }
                break;

            default:
                throw new PathExtractionException("Invalid path component type: " + readIonText(reader));
        }

        String[] annotations = extractAnnotations(reader);

        if (annotations.length > 0) {
            return new AnnotatedPathComponent(annotations, pathComponent);
        }

        return pathComponent;
    }

    private static String[] extractAnnotations(final IonReader reader) {
        String[] typeAnnotations = reader.getTypeAnnotations();

        final String[] annotations;
        final int offset;
        if (typeAnnotations.length > 0 && WILDCARD_ESCAPE_ANNOTATION.equals(typeAnnotations[0])) {
            annotations = new String[typeAnnotations.length - 1];
            offset = 1;
        } else {
            annotations = new String[typeAnnotations.length];
            offset = 0;
        }

        System.arraycopy(typeAnnotations, offset, annotations, 0, annotations.length);

        return annotations;
    }

    private static boolean isWildcard(final IonReader reader) {
        if (reader.stringValue().equals(Wildcard.TEXT)) {
            final String[] annotations = reader.getTypeAnnotations();
            return annotations.length == 0 || !WILDCARD_ESCAPE_ANNOTATION.equals(annotations[0]);
        }
        return false;
    }

    private static String readIonText(final IonReader reader) {
        StringBuilder out = new StringBuilder();
        try (IonWriter writer = newIonTextWriter(out)) {
            writer.writeValue(reader);
        } catch (IOException e) {
            throw new PathExtractionException(e);
        }
        return out.toString();
    }

    private static IonReader newIonReader(final String ionText) {
        return READER_BUILDER.build(ionText);
    }

    private static IonWriter newIonTextWriter(final StringBuilder out) {
        return WRITER_BUILDER.build(out);
    }
}
