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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonParser;

import org.junit.Assert;

import software.amazon.ion.IonReader;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonValue;
import software.amazon.ion.system.IonSystemBuilder;

import java.io.IOException;
import java.math.BigInteger;
import org.junit.Test;

@SuppressWarnings("resource")
public class IonParserTest
{
    @Test
    public void testGetNumberType() throws Exception {
        IonSystem ion = IonSystemBuilder.standard().build();
 
        IonValue ionInt = ion.newInt(Integer.MAX_VALUE);
        IonParser intParser = new IonFactory().createParser(ionInt);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, intParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.INT, intParser.getNumberType());
 
        IonValue ionLong = ion.newInt(Long.MAX_VALUE);
        IonParser longParser = new IonFactory().createParser(ionLong);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, longParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.LONG, longParser.getNumberType());
 
        IonValue ionBigInt = ion.newInt(new BigInteger(Long.MAX_VALUE + "1"));
        IonParser bigIntParser = new IonFactory().createParser(ionBigInt);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_INT, bigIntParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.BIG_INTEGER, bigIntParser.getNumberType());
 
        // JoiParser is currently deficient with decimals -- all decimals are reported as Double. So this is all we can test.
        IonValue ionDecimal = ion.newDecimal(Double.MAX_VALUE);
        IonParser floatParser = new IonFactory().createParser(ionDecimal);
        Assert.assertEquals(JsonToken.VALUE_NUMBER_FLOAT, floatParser.nextToken());
        Assert.assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
    }

    @Test
    public void testFloatType() throws IOException
    {
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
        final IonParser floatParser = new IonFactory().createParser(reader);
        Assert.assertEquals(JsonParser.NumberType.DOUBLE, floatParser.getNumberType());
    }
}
