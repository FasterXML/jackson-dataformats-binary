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

package tools.jackson.dataformat.ion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("resource")
public class IonParserTest
{
    private final IonFactory ionFactory = new IonFactory();

    private final static ObjectReadContext EMPTY_READ_CTXT = ObjectReadContext.empty();

    @Test
    public void testGetNumberTypeAndValue() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();

        Integer intValue = Integer.MAX_VALUE;
        IonValue ionInt = ion.newInt(intValue);

        IonParser intParser = ionFactory.createParser(EMPTY_READ_CTXT, ionInt);
        assertEquals(JsonToken.VALUE_NUMBER_INT, intParser.nextToken());
        assertEquals(JsonParser.NumberType.INT, intParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.UNKNOWN, intParser.getNumberTypeFP());
        assertEquals(intValue, intParser.getNumberValue());

        Long longValue = Long.MAX_VALUE;
        IonValue ionLong = ion.newInt(longValue);
        IonParser longParser = ionFactory.createParser(EMPTY_READ_CTXT, ionLong);
        assertEquals(JsonToken.VALUE_NUMBER_INT, longParser.nextToken());
        assertEquals(JsonParser.NumberType.LONG, longParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.UNKNOWN, intParser.getNumberTypeFP());
        assertEquals(longValue, longParser.getNumberValue());

        BigInteger bigIntValue = new BigInteger(Long.MAX_VALUE + "1");
        IonValue ionBigInt = ion.newInt(bigIntValue);
        IonParser bigIntParser = ionFactory.createParser(EMPTY_READ_CTXT, ionBigInt);
        assertEquals(JsonToken.VALUE_NUMBER_INT, bigIntParser.nextToken());
        assertEquals(JsonParser.NumberType.BIG_INTEGER, bigIntParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.UNKNOWN, intParser.getNumberTypeFP());
        assertEquals(bigIntValue, bigIntParser.getNumberValue());

        Double decimalValue = Double.MAX_VALUE;
        IonValue ionDecimal = ion.newDecimal(decimalValue);
        IonParser decimalParser = ionFactory.createParser(EMPTY_READ_CTXT, ionDecimal);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, decimalParser.nextToken());
        assertEquals(JsonParser.NumberType.BIG_DECIMAL, decimalParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.BIG_DECIMAL, decimalParser.getNumberTypeFP());
        assertEquals(0, new BigDecimal("" + decimalValue).compareTo((BigDecimal) decimalParser.getNumberValue()));

        Double floatValue = Double.MAX_VALUE;
        IonValue ionFloat = ion.newFloat(floatValue);
        IonParser floatParser = ionFactory.createParser(EMPTY_READ_CTXT, ionFloat);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, floatParser.nextToken());
        assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
        // [dataformats-binary#490]: float coerces to double
        assertEquals(JsonParser.NumberTypeFP.DOUBLE64, floatParser.getNumberTypeFP());
        assertEquals(floatValue, floatParser.getNumberValue());

        BigDecimal bigDecimalValue = new BigDecimal(Double.MAX_VALUE + "1");
        IonValue ionBigDecimal = ion.newDecimal(bigDecimalValue);
        IonParser bigDecimalParser = ionFactory.createParser(EMPTY_READ_CTXT, ionBigDecimal);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, bigDecimalParser.nextToken());
        assertEquals(JsonParser.NumberType.BIG_DECIMAL, bigDecimalParser.getNumberType());
        assertEquals(JsonParser.NumberTypeFP.BIG_DECIMAL, bigDecimalParser.getNumberTypeFP());
        assertEquals(0, bigDecimalValue.compareTo((BigDecimal) bigDecimalParser.getNumberValue()));
    }

    @Test
    public void testFloatType() throws IOException
    {
        final ObjectReadContext ctxt = ObjectReadContext.empty();

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
        final IonParser floatParser = ionFactory.createParser(ctxt, reader);
        assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
    }

    @Test
    public void testGetTypeId() throws IOException {
        String className = "com.example.Struct";
        final byte[] data =  ("'" + className + "'::{ foo: \"bar\" }").getBytes("UTF-8");

        IonSystem ion = IonSystemBuilder.standard().build();
        IonReader reader = ion.newReader(data, 0, data.length);
        IonFactory factory = new IonFactory();
        IonParser parser = factory.createParser(ObjectReadContext.empty(), reader);

        parser.nextToken(); // advance to find START_OBJECT

        assertEquals(className, parser.getTypeId());
    }

    @Test
    public void testParserCapabilities() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();

        Integer intValue = Integer.MAX_VALUE;
        IonValue ionInt = ion.newInt(intValue);

        try (IonParser p = new IonFactory().createParser(ObjectReadContext.empty(), ionInt)) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.streamReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }

    @Test
    public void testIonExceptionIsWrapped() throws Exception {
        assertThrows(StreamReadException.class, () -> {
            try (IonParser parser = (IonParser) ionFactory.createParser(EMPTY_READ_CTXT, "[  12, true ) ]")) {
                assertEquals(JsonToken.START_ARRAY, parser.nextToken());
                assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
                assertEquals(12, parser.getIntValue());
                assertEquals(JsonToken.VALUE_TRUE, parser.nextValue());
                parser.nextValue();
            }
        });
    }

    @Test
    public void testUnknownSymbolExceptionForValueIsWrapped() throws Exception {
        assertThrows(StreamReadException.class, () -> {
            try (IonParser parser = (IonParser) ionFactory.createParser(EMPTY_READ_CTXT, "[  12, $99 ]")) {
                assertEquals(JsonToken.START_ARRAY, parser.nextToken());
                assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
                assertEquals(12, parser.getIntValue());
                assertEquals(JsonToken.VALUE_STRING, parser.nextValue());
                parser.getValueAsString(); // Should encounter unknown symbol and fail
            }
        });
    }

    @Test
    public void testUnknownSymbolExceptionForFieldNameIsWrapped() throws Exception {
        assertThrows(StreamReadException.class, () -> {
            try (IonParser parser = (IonParser) ionFactory.createParser(EMPTY_READ_CTXT, "{  a: 1, $99: 2 }")) {
                assertEquals(JsonToken.START_OBJECT, parser.nextToken());
                assertEquals(JsonToken.PROPERTY_NAME, parser.nextToken());
                assertEquals("a", parser.currentName());
                assertEquals("a", parser.getString());
                assertEquals("a", parser.getValueAsString());
                assertEquals("a", parser.getValueAsString("b"));
                assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
                assertEquals(1, parser.getIntValue());
                parser.nextValue(); // Should encounter unknown symbol and fail
            }
        });
    }

    @Test
    public void testUnknownSymbolExceptionForAnnotationIsWrapped() throws Exception {
        assertThrows(StreamReadException.class, () -> {
            try (IonParser parser = (IonParser) ionFactory.createParser(EMPTY_READ_CTXT, "{  a: $99::1 }")) {
                assertEquals(JsonToken.START_OBJECT, parser.nextToken());
                assertEquals(JsonToken.PROPERTY_NAME, parser.nextToken());
                assertEquals("a", parser.currentName());
                assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextValue());
                assertEquals(1, parser.getIntValue());
                parser.getTypeAnnotations(); // Should encounter unknown symbol and fail
            }
        });
    }
}
