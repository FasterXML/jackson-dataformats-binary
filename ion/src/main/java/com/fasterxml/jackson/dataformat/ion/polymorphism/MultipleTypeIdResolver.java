/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
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

package com.fasterxml.jackson.dataformat.ion.polymorphism;

import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;

/**
 * This is an extension of {@link TypeIdResolver} for serializing and deserializing polymorphic types. The vanilla
 * implementation of TypeIdResolver only enables a single type id to be serialized with a value, which is not always
 * sufficient for every consumer of a value with polymorphic typing to identify and instantiate the appropriate type.
 * <p/>
 * This allows more robust polymorphism support, in that consumers of a serialized polymorphic object do not need
 * complete type information. As long as their deserializer can resolve (or, understand) one of the annotated type ids,
 * they can still perform polymorphic deserialization. An example:
 * <p/>
 * <pre>
 * class BasicMessage {
 *      String id;
 *      String message;
 * }
 * class SocialMediaMessage extends BasicMessage {
 *      boolean cool;
 * }
 *
 * Let's say we serialized a value into:
 *
 *      {id="123",message="hi",cool=false}.
 *
 * The existing polymorphism support may have annotated this as the following Ion:
 *
 *      SocialMediaMessage::{id="123",message="hi",cool=false}
 *
 * But a consumer who only has BasicMessage in its classloader won't know (or care) what a SocialMediaMessage is, and be
 * stuck. Using this interface enables the following serialization:
 *
 *      SocialMediaMessage::BasicMessage::{id="123",message="hi",cool=false}
 *
 * About particular implementations:
 * <p/>
 * Ion serialization should be using {@link IonAnnotationTypeSerializer}, which is polymorphism-aware, but how should a
 * MultipleTypeIdResolver handle a call to the non-polymorphic {@link TypeIdResolver#idFromValue(Object)}? I'd probably
 * do something like {@code return selectId(idsFromValue(value)); }, to keep things working when serializing
 * to non-Ion formats. Throwing a runtime exception is another idea, if you want to forbid non-Ion serialization.
 */
public interface MultipleTypeIdResolver extends TypeIdResolver {

    /**
     * Provides a list of valid (polymorphic) type ids for the given value.
     *
     * @param value Java object to be serialized.
     * @return An array of zero or more ids indicating valid types this value may be viewed as. May not be null.
     */
    String[] idsFromValue(Object value);

    /**
     * Given a set of type ids, select the most 'relevant'. We're not just doing something simple (like picking the
     * first one) because it might not even be present in the classloader, or it might be a supertype of the others,
     * or have some other reason for not being the best fit for a value. The heuristic is implementation-specific, but
     * I suggest choosing the id with the most narrow type present in the classloader. The returned id can be passed into
     * {@link TypeIdResolver#typeFromId} to get a {@link com.fasterxml.jackson.databind.JavaType}. It is a invariant
     * on this method that its output, if non-null, be valid input for {@link TypeIdResolver#typeFromId} of the
     * same TypeIdResolver instance.
     * <p/>
     * Note that we're not resolving the array of ids directly into a JavaType because there is code (in the Jackson
     * package, not ours) which consumes the id String itself, not the JavaType object.
     *
     * @param ids Type ids from a jsonified object. May not be null.
     * @return The selected id, or null if input was empty or contained no resolvable id
     */
    String selectId(String[] ids);

}
