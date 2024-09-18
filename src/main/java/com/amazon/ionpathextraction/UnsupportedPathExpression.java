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

package com.amazon.ionpathextraction;

/**
 * Thrown when trying to build a "strict" PathExtractor if a SearchPath or set of paths is not supported.
 * A user should rewrite their extraction to match the strictness invariant or use the "legacy" PathExtractor.
 */
public class UnsupportedPathExpression extends RuntimeException {
    public UnsupportedPathExpression(final String msg) {
        super(msg);
    }
}
