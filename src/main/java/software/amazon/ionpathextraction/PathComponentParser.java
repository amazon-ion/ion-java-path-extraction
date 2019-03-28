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

import static software.amazon.ionpathextraction.utils.Preconditions.checkArgument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
 * Parses a search path ion expression into {@link PathComponent}s.
 */
final class PathComponentParser {

    private static final IonReaderBuilder READER_BUILDER = IonReaderBuilder.standard();
    private static final IonTextWriterBuilder WRITER_BUILDER = IonTextWriterBuilder.standard();

    private static final String WILDCARD_ESCAPE_ANNOTATION = "$ion_extractor_field";
    private static final String ANNOTATED_PATH_COMPONENT_TAG = "annotatedWith";

    // only has static methods, should not be invoked
    private PathComponentParser() {
    }

    static List<PathComponent> parse(final String ionPathExpression) {
        List<PathComponent> pathComponents;

        try (final IonReader reader = newIonReader(ionPathExpression)) {
            checkArgument(reader.next() != null, "ionPathExpression cannot be empty");
            checkArgument(reader.getType() == IonType.SEXP || reader.getType() == IonType.LIST,
                "ionPathExpression must be a s-expression or list");

            reader.stepIn();
            pathComponents = readStates(reader);
        } catch (IOException e) {
            throw new PathExtractionException(e);
        }

        return pathComponents;
    }

    private static List<PathComponent> readStates(final IonReader reader) {
        final List<PathComponent> pathComponents = new ArrayList<>();

        while (reader.next() != null) {
            pathComponents.add(readComponent(reader));
        }

        return pathComponents;
    }

    private static PathComponent readComponent(final IonReader reader) {
        switch (reader.getType()) {
            case SEXP:
            case LIST:
                return readAnnotatedPathComponent(reader);

            case INT:
                return new Index(reader.intValue());

            case STRING:
            case SYMBOL:
                if (isWildcard(reader)) {
                    return Wildcard.INSTANCE;
                }
                return new Text(reader.stringValue());

            default:
                throw new PathExtractionException("Invalid path component type: " + readIonText(reader));
        }
    }

    private static PathComponent readAnnotatedPathComponent(final IonReader reader) {
        reader.stepIn();

        checkArgument(reader.next() != null, "Invalid empty wrapped matcher");
        final PathComponent wrappedPathComponent = readComponent(reader);

        checkArgument(reader.next() != null, "Wrapped matcher must have a tag");
        final String wrapperType = readText(reader);
        checkArgument(ANNOTATED_PATH_COMPONENT_TAG.equals(wrapperType), "Unknown wrapped matcher tag: " + wrapperType);

        final List<String> annotations = new ArrayList<>();
        while (reader.next() != null) {
            final String annotation = readText(reader);
            annotations.add(annotation);
        }

        checkArgument(annotations.size() > 0, "annotatedWith wrapped matchers must have at least one annotation");

        AnnotatedPathComponent annotatedPathComponent = new AnnotatedPathComponent(annotations.toArray(new String[0]),
            wrappedPathComponent);

        reader.stepOut();
        return annotatedPathComponent;
    }

    private static boolean isWildcard(final IonReader reader) {
        if (reader.stringValue().equals(Wildcard.TEXT)) {
            final String[] annotations = reader.getTypeAnnotations();
            return annotations.length == 0 || !WILDCARD_ESCAPE_ANNOTATION.equals(annotations[0]);
        }
        return false;
    }

    private static String readText(final IonReader reader) {
        switch (reader.getType()) {
            case SYMBOL:
            case STRING:
                return reader.stringValue();

            default:
                throw new PathExtractionException("Invalid reader type, expecting String or Symbol got: "
                    + reader.getType());
        }
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
