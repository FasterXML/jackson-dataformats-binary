/*
 * Copyright 2014-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.databind.SerializationFeature;

public class EnumAsIonSymbolSerializationTest {
    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    @Test
    public void testUsingName() throws IOException {
        final IonObjectMapper mapper = newMapper();

        Assert.assertEquals(
            ION_SYSTEM.singleValue("SOME_VALUE"),
            mapper.writeValueAsIonValue(SomeEnum.SOME_VALUE));
    }

    @Test
    public void testUsingToString() throws IOException {
        final IonObjectMapper mapper = newMapper();

        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);

        Assert.assertEquals(
            ION_SYSTEM.singleValue("some_value"),
            mapper.writeValueAsIonValue(SomeEnum.SOME_VALUE));
    }

    private static IonObjectMapper newMapper() {
        final IonObjectMapper mapper = new IonObjectMapper(new IonFactory(null, ION_SYSTEM));
        mapper.registerModule(new EnumAsIonSymbolModule());
        return mapper;
    }

    private enum SomeEnum {
        SOME_VALUE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
