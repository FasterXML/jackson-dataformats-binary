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

package tools.jackson.dataformat.ion;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.SerializationFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumAsIonSymbolSerializationTest
{
    private enum SomeEnum {
        SOME_VALUE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    @Test
    public void testUsingName() throws Exception
    {
        final IonValue EXP = ION_SYSTEM.singleValue("SOME_VALUE");
        assertEquals(EXP,
                newMapper(false, false).writeValueAsIonValue(SomeEnum.SOME_VALUE));
        assertEquals(EXP,
                newMapper(true, false).writeValueAsIonValue(SomeEnum.SOME_VALUE));
    }

    @Test
    public void testUsingToString() throws Exception
    {
        final IonValue EXP = ION_SYSTEM.singleValue("some_value");
        assertEquals(EXP,
                newMapper(false, true).writeValueAsIonValue(SomeEnum.SOME_VALUE));
        assertEquals(EXP,
                newMapper(true, true).writeValueAsIonValue(SomeEnum.SOME_VALUE));
    }

    private static IonObjectMapper newMapper(boolean textual, boolean usingToString) {
        IonObjectMapper.Builder builder = textual
                ? IonObjectMapper.builderForTextualWriters(ION_SYSTEM)
                : IonObjectMapper.builderForBinaryWriters(ION_SYSTEM);

        return builder.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, usingToString)
                .addModule(new EnumAsIonSymbolModule())
                .build();
    }
}
