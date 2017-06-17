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

package com.fasterxml.jackson.dataformat.ion;

import java.io.*;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonGenerator;

import software.amazon.ion.IonDatagram;
import software.amazon.ion.IonLoader;
import software.amazon.ion.IonStruct;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonType;
import software.amazon.ion.system.IonSystemBuilder;

public class SimpleWriteTest
{
    // // // Actual tests; low level
    
    @Test
    public void testSimpleStructWriteText() throws Exception
    {
        IonFactory f = new IonFactory();
        f.setCreateBinaryWriters(false);
        StringWriter sw = new StringWriter();
        JsonGenerator gen = f.createGenerator(sw);
        _writeSimple(gen);
        // now parse and compare
        ionTextCompare(sw.toString());
        gen.close();
    }

    @Test
    public void testSimpleStructWriteBinary() throws Exception
    {
        IonFactory f = new IonFactory();
        f.setCreateBinaryWriters(true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator gen = f.createGenerator(bos);
        _writeSimple(gen);
        byte[] data = bos.toByteArray();
        assertNotNull(data);
        ionBinaryCompare(data);
        gen.close();
    }

    /**
     * There were some problems with flushing (or lack thereof), so let's
     * see that outputting using an encoding stream also works ok
     */
    @Test
    public void testSimpleStructWriteTextViaOutputStream() throws Exception
    {
        IonFactory f = new IonFactory();
        f.setCreateBinaryWriters(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = f.createGenerator(out);
        _writeSimple(gen);
        ionTextCompare(out.toString("UTF-8"));
        gen.close();
    }
    
    // // // Helper methods
        
    private void _writeSimple(JsonGenerator gen) throws IOException
    {
        gen.writeStartObject();
        gen.writeStringField("a", "value");
        gen.writeNumberField("b", 42);
        ((IonGenerator)gen).writeFieldName("c");
        ((IonGenerator)gen).writeNull(IonType.INT);
        gen.writeEndObject();
        gen.close();
    }
    
    IonSystem ion = IonSystemBuilder.standard().build();
    IonDatagram expected;
    
    @Before
    public void initializeExpectedDatagram() {
        IonStruct struct = ion.newEmptyStruct();
        struct.add("a").newString("value");
        struct.add("b").newInt(42);
        struct.add("c").newNull(IonType.INT);
        expected = ion.newDatagram();
        expected.add(struct);
    }
    
    private void ionTextCompare(String generatedTextIon) {
        IonLoader loader = ion.newLoader();
        IonDatagram loadedDatagram = loader.load(generatedTextIon);
        // the expected value is always the same {a:"value",b:42}
        assertEquals(expected, loadedDatagram);
    }
    
    private void ionBinaryCompare(byte[] generatedBinaryIon) {
        IonLoader loader = ion.newLoader();
        IonDatagram loadedDatagram = loader.load(generatedBinaryIon);
        // the expected value is always the same {a:"value",b:42}
        assertEquals(expected, loadedDatagram);
    }
}
