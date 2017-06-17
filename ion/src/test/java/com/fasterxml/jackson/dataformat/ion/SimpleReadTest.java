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
import com.fasterxml.jackson.dataformat.ion.IonFactory;

public class SimpleReadTest {
    // // // Actual tests; low level

    @Test
    public void testSimpleStructRead() throws IOException
    {
        IonFactory f = new IonFactory();
        JsonParser jp = f.createParser("{a:\"value\",b:42, c:null}");
        assertEquals(JsonToken.START_OBJECT, jp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("a", jp.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("value", jp.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, jp.nextValue());
        assertEquals("b", jp.getCurrentName());
        assertEquals(42, jp.getIntValue());
        assertEquals(JsonToken.VALUE_NULL, jp.nextValue());
        assertEquals("c", jp.getCurrentName());
        assertEquals(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();
    }

    @Test
    public void testSimpleListRead() throws IOException
    {
        IonFactory f = new IonFactory();
        JsonParser jp = f.createParser("[  12, true, null, \"abc\" ]");
        assertEquals(JsonToken.START_ARRAY, jp.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, jp.nextValue());
        assertEquals(12, jp.getIntValue());
        assertEquals(JsonToken.VALUE_TRUE, jp.nextValue());
        assertEquals(JsonToken.VALUE_NULL, jp.nextValue());
        assertEquals(JsonToken.VALUE_STRING, jp.nextValue());
        assertEquals("abc", jp.getText());
        assertEquals(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    @Test
    public void testSimpleStructAndArray() throws IOException
    {
        IonFactory f = new IonFactory();
        JsonParser jp = f.createParser("{a:[\"b\",\"c\"], b:null}");
        assertEquals(JsonToken.START_OBJECT, jp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("a", jp.getCurrentName());
        assertEquals(JsonToken.START_ARRAY, jp.nextToken());
        assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("b", jp.getText());
        assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("c", jp.getText());
        assertEquals(JsonToken.END_ARRAY, jp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("b", jp.getCurrentName());
        assertEquals(JsonToken.VALUE_NULL, jp.nextToken());
        assertEquals("b", jp.getCurrentName());
        assertEquals(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();
    }
    
    @Test
    public void testMixed() throws IOException
    {
        IonFactory f = new IonFactory();
        JsonParser jp = f.createParser("{a:[ 1, { b:  13}, \"xyz\" ], c:null, d:true}");
        assertEquals(JsonToken.START_OBJECT, jp.nextToken());
        assertEquals(JsonToken.START_ARRAY, jp.nextValue());
        //assertEquals("a", jp.getCurrentName());        
        assertEquals(JsonToken.VALUE_NUMBER_INT, jp.nextValue());
        assertNull(jp.getCurrentName());
        assertEquals(1, jp.getIntValue());
        assertEquals(JsonToken.START_OBJECT, jp.nextValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, jp.nextValue());
        assertEquals("b", jp.getCurrentName());
        assertEquals(13, jp.getIntValue());
        assertEquals(JsonToken.END_OBJECT, jp.nextValue());
        assertEquals(JsonToken.VALUE_STRING, jp.nextValue());
        assertEquals("xyz", jp.getText());
        assertNull(jp.getCurrentName());
        assertEquals(JsonToken.END_ARRAY, jp.nextValue());
        assertEquals(JsonToken.VALUE_NULL, jp.nextValue());
        assertEquals("c", jp.getCurrentName());
        assertEquals(JsonToken.VALUE_TRUE, jp.nextValue());
        assertEquals("d", jp.getCurrentName());
        
        assertEquals(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();
    }

    @Test
    public void testNullIonType() throws IOException {
        IonFactory f = new IonFactory();
        JsonParser jp = f.createParser("{a:\"value\",b:42, c:null.int}");
        assertEquals(JsonToken.START_OBJECT, jp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("a", jp.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("value", jp.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, jp.nextValue());
        assertEquals("b", jp.getCurrentName());
        assertEquals(42, jp.getIntValue());
        assertEquals(JsonToken.VALUE_NULL, jp.nextValue());
        assertEquals("c", jp.getCurrentName());
        assertEquals(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();        
    }
}
