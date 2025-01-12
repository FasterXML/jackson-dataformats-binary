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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import static org.junit.Assert.*;

public class DataBindReadTest {
    static class MyBean {
        public String a;
        public int b;
        @JsonIgnore public Object ignore;
        public byte[] blob;
    }

    static class BeanToo { }

    // Not the best place for test but will have to do
    @Test
    public void testMapperCopy() throws IOException
    {
        IonObjectMapper vanilla = IonObjectMapper.builder().build();
        ObjectMapper copy = vanilla.copy();
        assertNotSame(vanilla, copy);
    }

    @Test
    public void testSimple() throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builder().build();
        MyBean bean = m.readValue("{a: \"...\", \"b\" : 39, blob:{{SGVsbG8h}} }", MyBean.class);
        assertEquals("...", bean.a);
        assertEquals(39, bean.b);
        assertArrayEquals("Hello!".getBytes(), bean.blob);

        // and then same with symbols as Strings, and implicit coercion
        bean = m.readValue("{'a': bc, b : '14' }", MyBean.class);
        assertEquals("bc", bean.a);
        assertEquals(14, bean.b);

        // and some ion timestamp (not well formed json).
        bean = m.readValue("{a:1999-08-18T00:00:01-00:00, b:2}", MyBean.class);
        assertEquals("1999-08-18T00:00:01-00:00", bean.a);
        assertEquals(2, bean.b);

    }

    @Test
    public void testJsonIgnoreProperty() throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builder().build();
        MyBean bean = m.readValue("{a: \"...\", ignore:{x:\"y\"}, \"b\" : 39 }", MyBean.class);
        assertEquals("...", bean.a);
        assertEquals(39, bean.b);
        assertNull(bean.ignore);
    }

    /**
     * Test reading an IonValue, which also happens to not be at the top level.
     */
    @Test
    public void testFromIon() throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builder().build();
        IonSystem ion = IonSystemBuilder.standard().build();
        IonValue value = ion.singleValue("{payload: {'a': bc, b : '14' }}");
        MyBean bean = m.readValue(((IonStruct) value).get("payload"), MyBean.class);

        assertEquals("bc", bean.a);
        assertEquals(14, bean.b);
    }

    /**
     * Test reading some basic Ion types that aren't structs/JavaBeans
     */
    @Test
    public void testBasicTypes() throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builder().build();
        IonSystem ion = IonSystemBuilder.standard().build();
        assertNull(m.readValue(ion.newNull(), Object.class));
        assertEquals("foo", m.readValue(ion.newString("foo"), String.class));
    }

    /**
     * Test reading IonValues from a reader where the values aren't at the
     * top level, making sure that no tokens are dropped and that the reader
     * is left open.
     */
    @Test
    public void testMultipleReads() throws IOException
    {
        IonObjectMapper m = IonObjectMapper.builder().build();
        IonSystem ion = IonSystemBuilder.standard().build();

        IonReader reader = ion.newReader("[foo, bar, baz]");
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals("foo", m.readValue(reader, String.class));
        assertEquals("bar", m.readValue(reader, String.class));
        assertEquals("baz", m.readValue(reader, String.class));
        reader.stepOut();
        reader.close();
    }
}
