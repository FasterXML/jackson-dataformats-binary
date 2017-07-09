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

package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.ion.PackageVersion;

import software.amazon.ion.Timestamp;

/**
 * A module which allows for the direct serialization to and from IonValue fields. The POJO can declare fields of type
 * IonValue (or a subclass) and the direct value will be provided.
 */
public class IonValueModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public IonValueModule() {
        super("IonValueModule", PackageVersion.VERSION);
        addSerializer(new TimestampSerializer());
        addSerializer(new IonValueSerializer());

        setDeserializers(new DeserializersEx());
        addDeserializer(Timestamp.class, new TimestampDeserializer());
    }
}
