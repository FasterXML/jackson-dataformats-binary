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

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonList;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.system.IonWriterBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DataBindWriteTest {
    
    static class MyBean {
        public String getA() { return "value"; }
        public int getB() { return 42; }
    }
    
    // initialize an equivalent MyBean in Ion
    
    IonSystem ion = IonSystemBuilder.standard().build();
    IonDatagram expectedMyBean;
    
    @Before
    public void initializeExpectedMyBean() {
        expectedMyBean = ion.newDatagram();
        IonStruct struct = ion.newEmptyStruct();
        struct.add("a").newString("value");
        struct.add("b").newInt(42);
        expectedMyBean = ion.newDatagram();
        expectedMyBean.add(struct);
    }

    // initialize an equivalent array [1,2,3] in Ion
    
    IonDatagram expectedArray;
    
    @Before
    public void initializeExpectedArray() {
        expectedArray = ion.newDatagram();
        IonList list = ion.newEmptyList();
        list.add().newInt(1);
        list.add().newInt(2);
        list.add().newInt(3);
        expectedArray.add(list);
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */
   
    @Test
    public void testSimpleObjectWriteText() throws Exception
    {
        IonObjectMapper m = IonObjectMapper.builderForTextualWriters().build();
        // now parse it using IonLoader and compare
        IonDatagram loadedDatagram = ion.newLoader().load(m.writeValueAsString(new MyBean()));
        assertEquals(expectedMyBean, loadedDatagram);
    }

    @Test
    public void testSimpleObjectWriteBinary() throws Exception
    {
        byte[] data = _writeAsBytes(new MyBean());
        IonDatagram loadedDatagram = ion.newLoader().load(data);
        assertEquals(expectedMyBean, loadedDatagram);
    }

    @Test
    public void testSimpleObjectWriteIon() throws Exception
    {
        IonStruct struct = ion.newEmptyStruct();
        IonWriter writer = ion.newWriter(struct);
        writer.setFieldName("payload");
        new IonObjectMapper().writeValue(writer, new MyBean());
        writer.close();

        IonStruct expectedStruct = ion.newEmptyStruct();
        expectedStruct.put("payload", expectedMyBean.get(0).clone());
        assertEquals(expectedStruct, struct);
    }

    @Test
    public void testWriteBasicTypes() throws Exception
    {
        IonObjectMapper m = new IonObjectMapper(IonFactory.builderForTextualWriters()
                .ionSystem(ion)
                .build());

        assertEquals(ion.newString("foo"), m.writeValueAsIonValue("foo"));
        assertEquals(ion.newBool(true), m.writeValueAsIonValue(true));
        assertEquals(ion.newInt(42), m.writeValueAsIonValue(42));
        assertEquals(ion.newNull(), m.writeValueAsIonValue(null));
    }

    @Test
    public void testIntArrayWriteText() throws Exception
    {
        IonObjectMapper m = new IonObjectMapper(IonFactory.forTextualWriters());
        IonDatagram loadedDatagram = ion.newLoader().load(m.writeValueAsString(new int[] { 1, 2, 3 } ));
        assertEquals(expectedArray, loadedDatagram);
    }

    @Test
    public void testIntArrayWriteBinary() throws Exception
    {
        byte[] data = _writeAsBytes(new int[] { 1, 2, 3 });
        assertNotNull(data);
        IonDatagram loadedDatagram = ion.newLoader().load(data);
        assertEquals(expectedArray, loadedDatagram);
    }

    @Test
    public void testReusingBinaryIonWriter() throws Exception
    {
        _testIonWriterReuse(IonBinaryWriterBuilder.standard());
    }

    @Test
    public void testReusingTextIonWriter() throws Exception
    {
        _testIonWriterReuse(IonTextWriterBuilder.standard());
    }

    // // Helper methods

    private byte[] _writeAsBytes(Object ob) throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builderForBinaryWriters().build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        m.writeValue(out, ob);
        return out.toByteArray();
    }

    private void _testIonWriterReuse(IonWriterBuilder ionWriterBuilder) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        IonWriter ionWriter = ionWriterBuilder.build(byteArrayOutputStream);

        IonObjectMapper ionObjectMapper = new IonObjectMapper();
        ionObjectMapper.writeValue(ionWriter, "Animal");
        ionObjectMapper.writeValue(ionWriter, "Vegetable");
        ionObjectMapper.writeValue(ionWriter, "Mineral");
        ionWriter.close();

        byte[] data = byteArrayOutputStream.toByteArray();
        assertNotNull(data);

        IonReader ionReader = IonReaderBuilder.standard().build(data);
        assertEquals(IonType.STRING, ionReader.next());
        assertEquals("Animal", ionReader.stringValue());
        assertEquals(IonType.STRING, ionReader.next());
        assertEquals("Vegetable", ionReader.stringValue());
        assertEquals(IonType.STRING, ionReader.next());
        assertEquals("Mineral", ionReader.stringValue());
    }
}
