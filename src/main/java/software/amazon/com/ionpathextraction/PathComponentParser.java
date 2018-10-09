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

package software.amazon.com.ionpathextraction;

import static software.amazon.com.ionpathextraction.utils.Preconditions.checkArgument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import software.amazon.com.ionpathextraction.exceptions.PathExtractionException;
import software.amazon.com.ionpathextraction.pathcomponents.Index;
import software.amazon.com.ionpathextraction.pathcomponents.PathComponent;
import software.amazon.com.ionpathextraction.pathcomponents.Text;
import software.amazon.com.ionpathextraction.pathcomponents.Wildcard;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonType;
import software.amazon.ion.IonWriter;
import software.amazon.ion.system.IonSystemBuilder;

/**
 * Parses a search path ion expression into {@link PathComponent}s.
 */
class PathComponentParser {

    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    private static final String WILDCARD_ESCAPE_ANNOTATION = "$ion_extractor_field";

    List<PathComponent> parse(final String ionPathExpression) {
        List<PathComponent> pathComponents;

        try (final IonReader reader = ION_SYSTEM.newReader(ionPathExpression)) {
            checkArgument(reader.next() != null, "ionPathExpression cannot be empty");
            checkArgument(reader.getType() == IonType.SEXP, "ionPathExpression must be a s-expression");

            reader.stepIn();
            pathComponents = readStates(reader);
        } catch (IOException e) {
            throw new PathExtractionException(e);
        }

        return pathComponents;
    }

    private List<PathComponent> readStates(final IonReader reader) {
        final List<PathComponent> pathComponents = new ArrayList<>();

        while (reader.next() != null) {
            switch (reader.getType()) {
                case INT:
                    pathComponents.add(new Index(reader.intValue()));
                    break;

                case STRING:
                case SYMBOL:
                    if (isWildcard(reader)) {
                        pathComponents.add(Wildcard.INSTANCE);
                    } else {
                        pathComponents.add(new Text(reader.stringValue()));
                    }
                    break;

                default:
                    throw new PathExtractionException("Invalid path component type: " + readIonText(reader));
            }
        }

        return pathComponents;
    }

    private String readIonText(final IonReader reader) {
        StringBuilder out = new StringBuilder();
        try (IonWriter writer = ION_SYSTEM.newTextWriter(out)) {
            writer.writeValue(reader);
        } catch (IOException e) {
            throw new PathExtractionException(e);
        }
        return out.toString();
    }

    private boolean isWildcard(final IonReader reader) {
        if (reader.stringValue().equals(Wildcard.TEXT)) {
            for (final Iterator<String> iter = reader.iterateTypeAnnotations(); iter.hasNext(); ) {
                final String annotation = iter.next();
                if (WILDCARD_ESCAPE_ANNOTATION.equals(annotation)) {
                    return false;
                }
            }

            return true;
        }
        return false;
    }
}
