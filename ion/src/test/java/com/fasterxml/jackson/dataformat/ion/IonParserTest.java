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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadCapability;

import org.junit.Assert;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;

@SuppressWarnings("resource")
public class IonParserTest
{
    @Test
    public void testGetNumberTypeAndValue() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();

        Integer intValue = Integer.MAX_VALUE;
        IonValue ionInt = ion.newInt(intValue);
        IonParser intParser = new IonFactory().createParser(ionInt);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, intParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.INT, intParser.getNumberType());
        Assert.assertEquals(intValue, intParser.getNumberValue());

        Long longValue = Long.MAX_VALUE;
        IonValue ionLong = ion.newInt(longValue);
        IonParser longParser = new IonFactory().createParser(ionLong);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, longParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.LONG, longParser.getNumberType());
        Assert.assertEquals(longValue, longParser.getNumberValue());

        BigInteger bigIntValue = new BigInteger(Long.MAX_VALUE + "1");
        IonValue ionBigInt = ion.newInt(bigIntValue);
        IonParser bigIntParser = new IonFactory().createParser(ionBigInt);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, bigIntParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.BIG_INTEGER, bigIntParser.getNumberType());
        Assert.assertEquals(bigIntValue, bigIntParser.getNumberValue());

        Double decimalValue = Double.MAX_VALUE;
        IonValue ionDecimal = ion.newDecimal(decimalValue);
        IonParser decimalParser = new IonFactory().createParser(ionDecimal);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_FLOAT, decimalParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.BIG_DECIMAL, decimalParser.getNumberType());
        Assert.assertEquals(0, new BigDecimal("" + decimalValue).compareTo((BigDecimal) decimalParser.getNumberValue()));

        Double floatValue = Double.MAX_VALUE;
        IonValue ionFloat = ion.newFloat(floatValue);
        IonParser floatParser = new IonFactory().createParser(ionFloat);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_FLOAT, floatParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
        Assert.assertEquals(floatValue, floatParser.getNumberValue());

        BigDecimal bigDecimalValue = new BigDecimal(Double.MAX_VALUE + "1");
        IonValue ionBigDecimal = ion.newDecimal(bigDecimalValue);
        IonParser bigDecimalParser = new IonFactory().createParser(ionBigDecimal);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_FLOAT, bigDecimalParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.BIG_DECIMAL, bigDecimalParser.getNumberType());
        Assert.assertEquals(0, bigDecimalValue.compareTo((BigDecimal) bigDecimalParser.getNumberValue()));
    }

    @Test
    public void testFloatType() throws IOException {
        final byte[] data =  "{ score:0.291e0 }".getBytes();
        IonSystem ion = IonSystemBuilder.standard().build();
        final IonValue ionFloat = ion.newFloat(Float.MAX_VALUE);
        IonReader reader = ionFloat.getSystem().newReader(data, 0, data.length);
        // Find the object
        reader.next();
        // Step into the object
        reader.stepIn();
        // Step next.
        reader.next();
        // 30-Dec-2023, tatu: This is problematic as created parser is expected
        //    to point to `JsonToken.VALUE_NUMBER_FLOAT`, but `createParser()`
        //    does not initialize state. For now, `IonParser.getNumberType()` has
        //    special handling allowing this case but that should not be needed
        final IonParser floatParser = new IonFactory().createParser(reader);
        Assert.assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
    }

    @Test
    public void testGetTypeId() throws IOException {
        String className = "com.example.Struct";
        final byte[] data =  ("'" + className + "'::{ foo: \"bar\" }").getBytes();

        IonSystem ion = IonSystemBuilder.standard().build();
        IonReader reader = ion.newReader(data, 0, data.length);
        IonFactory factory = new IonFactory();
        IonParser parser = factory.createParser(reader);

        parser.nextToken(); // advance to find START_OBJECT

        Assert.assertEquals(className, parser.getTypeId());
    }

    @Test
    public void testParserCapabilities() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();

        Integer intValue = Integer.MAX_VALUE;
        IonValue ionInt = ion.newInt(intValue);

        try (IonParser p = new IonFactory().createParser(ionInt)) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            Assert.assertTrue(p.getReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }


    @Test(expected = JsonParseException.class)
    public void testIonExceptionIsWrapped() throws IOException {
        IonFactory f = new IonFactory();
        try (IonParser parser = (IonParser) f.createParser("[  12, true ) ]")) {
            Assert.assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
            Assert.assertEquals(12, parser.getIntValue());
            Assert.assertEquals(JsonToken.VALUE_TRUE, parser.nextValue());
            parser.nextValue();
        }
    }

    @Test(expected = JsonParseException.class)
    public void testUnknownSymbolExceptionForValueIsWrapped() throws IOException {
        IonFactory f = new IonFactory();
        try (IonParser parser = (IonParser) f.createParser("[  12, $99 ]")) {
            Assert.assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
            Assert.assertEquals(12, parser.getIntValue());
            Assert.assertEquals(JsonToken.VALUE_STRING, parser.nextValue());
            parser.getValueAsString(); // Should encounter unknown symbol and fail
        }
    }

    @Test(expected = JsonParseException.class)
    public void testUnknownSymbolExceptionForFieldNameIsWrapped() throws IOException {
        IonFactory f = new IonFactory();
        try (IonParser parser = (IonParser) f.createParser("{  a: 1, $99: 2 }")) {
            Assert.assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            Assert.assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            Assert.assertEquals("a", parser.currentName());
            Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
            Assert.assertEquals(1, parser.getIntValue());
            parser.nextValue(); // Should encounter unknown symbol and fail
        }
    }

    @Test(expected = JsonParseException.class)
    public void testUnknownSymbolExceptionForAnnotationIsWrapped() throws IOException {
        IonFactory f = new IonFactory();
        try (IonParser parser = (IonParser) f.createParser("{  a: $99::1 }")) {
            Assert.assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            Assert.assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            Assert.assertEquals("a", parser.currentName());
            Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
            Assert.assertEquals(1, parser.getIntValue());
            parser.getTypeAnnotations(); // Should encounter unknown symbol and fail
        }
    }
}
