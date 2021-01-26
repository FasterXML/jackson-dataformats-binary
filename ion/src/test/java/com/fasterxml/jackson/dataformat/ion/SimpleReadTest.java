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

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class SimpleReadTest
{
    private final IonObjectMapper MAPPER = new IonObjectMapper();

    // // // Actual tests; low level

    @Test
    public void testSimpleStructRead() throws IOException
    {
        JsonParser p = MAPPER.createParser("{a:\"value\",b:42, c:null}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("a", p.currentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("value", p.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextValue());
        assertEquals("b", p.currentName());
        assertEquals(42, p.getIntValue());
        assertEquals(JsonToken.VALUE_NULL, p.nextValue());
        assertEquals("c", p.currentName());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    @Test
    public void testSimpleListRead() throws IOException
    {
        JsonParser p = MAPPER.createParser("[  12, true, null, \"abc\" ]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextValue());
        assertEquals(12, p.getIntValue());
        assertEquals(JsonToken.VALUE_TRUE, p.nextValue());
        assertEquals(JsonToken.VALUE_NULL, p.nextValue());
        assertEquals(JsonToken.VALUE_STRING, p.nextValue());
        assertEquals("abc", p.getText());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    @Test
    public void testSimpleStructAndArray() throws IOException
    {
        JsonParser p = MAPPER.createParser("{a:[\"b\",\"c\"], b:null}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("a", p.currentName());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("b", p.getText());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("c", p.getText());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("b", p.currentName());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals("b", p.currentName());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }
    
    @Test
    public void testMixed() throws IOException
    {
        JsonParser p = MAPPER.createParser("{a:[ 1, { b:  13}, \"xyz\" ], c:null, d:true}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.START_ARRAY, p.nextValue());
        //assertEquals("a", p.currentName());        
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextValue());
        assertNull(p.currentName());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.START_OBJECT, p.nextValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextValue());
        assertEquals("b", p.currentName());
        assertEquals(13, p.getIntValue());
        assertEquals(JsonToken.END_OBJECT, p.nextValue());
        assertEquals(JsonToken.VALUE_STRING, p.nextValue());
        assertEquals("xyz", p.getText());
        assertNull(p.currentName());
        assertEquals(JsonToken.END_ARRAY, p.nextValue());
        assertEquals(JsonToken.VALUE_NULL, p.nextValue());
        assertEquals("c", p.currentName());
        assertEquals(JsonToken.VALUE_TRUE, p.nextValue());
        assertEquals("d", p.currentName());
        
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    @Test
    public void testNullIonType() throws IOException {
        JsonParser p = MAPPER.createParser("{a:\"value\",b:42, c:null.int}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("a", p.currentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("value", p.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextValue());
        assertEquals("b", p.currentName());
        assertEquals(42, p.getIntValue());
        assertEquals(JsonToken.VALUE_NULL, p.nextValue());
        assertEquals("c", p.currentName());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        p.close();        
    }
}
