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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * End to end test verifying we can serialize sexps
 */
public class GenerateSexpTest {

    private IonSystem ionSystem;
    private IonObjectMapper mapper;

    @Before
    public void setup() {
        this.ionSystem = IonSystemBuilder.standard().build();
        this.mapper = IonObjectMapper.builderForTextualWriters(ionSystem).build();
    }

    @Test
    public void topLevel() throws IOException {
        Assert.assertEquals(
            ionSystem.singleValue("(foo \"bar\")"),
            mapper.writeValueAsIonValue(new SexpObject("foo", "bar")));
    }

    @Test
    public void inList() throws IOException {
        Assert.assertEquals(
            ionSystem.singleValue("[(foo \"bar\"), (baz \"qux\")]"),
            mapper.writeValueAsIonValue(
                Arrays.asList(new SexpObject("foo", "bar"), new SexpObject("baz", "qux"))));
    }

    @Test
    public void inObject() throws IOException {
        Assert.assertEquals(
            ionSystem.singleValue("{sexpField:(foo \"bar\")}"),
            mapper.writeValueAsIonValue(new SexpObjectContainer(new SexpObject("foo", "bar"))));
    }

    @Test
    public void inOtherSexp() throws IOException {
        Assert.assertEquals(
            ionSystem.singleValue("(foo (bar \"baz\"))"),
            mapper.writeValueAsIonValue(new SexpObject("foo", new SexpObject("bar", "baz"))));
    }

    @Test
    public void generatorUsedInStreamingWriteText() throws IOException {
        Assert.assertArrayEquals("(foo 0)".getBytes(), toBytes(new SexpObject("foo", 0), mapper));
    }

    @Test
    public void generatorUsedInStreamingWriteBinary() throws IOException {
        byte[] expectedBytes = null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             IonWriter writer = ionSystem.newBinaryWriter(baos)) {
            ionSystem.singleValue("(foo 0)").writeTo(writer);
            writer.finish();
            expectedBytes = baos.toByteArray();
        }

        IonObjectMapper binaryMapper = IonObjectMapper.builder(IonFactory.forBinaryWriters())
                .build();
        Assert.assertArrayEquals(expectedBytes, toBytes(new SexpObject("foo", 0), binaryMapper));
    }

    private byte[] toBytes(Object object, IonObjectMapper mapper) throws IOException {
        byte[] bytes = null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            mapper.writeValue(baos, object);
            bytes = baos.toByteArray();
        }

        return bytes;
    }

    static class SexpObjectContainer {
        private SexpObject sexpField;

        SexpObjectContainer(SexpObject sexpField) {
            this.sexpField = sexpField;
        }

        public SexpObject getSexpField() {
            return sexpField;
        }
    }

    // Create some pojo that defines a custom serializer that creates an IonSexp
    @JsonSerialize(using=SexpObjectSerializer.class)
    private static class SexpObject {
        private String symbolField;
        private Object objectField;

        SexpObject(String symbolField, Object objectField) {
            this.symbolField = symbolField;
            this.objectField = objectField;
        }

        public String getSymbolField() {
            return symbolField;
        }

        public Object getObjectField() {
            return objectField;
        }
    }

    private static class SexpObjectSerializer extends ValueSerializer<SexpObject> {
        @Override
        public void serialize(SexpObject value, JsonGenerator jsonGenerator,
                SerializerProvider provider)
        {
            final IonGenerator ionGenerator = (IonGenerator) jsonGenerator;

            ionGenerator.writeStartSexp();
            ionGenerator.writeSymbol(value.getSymbolField());
            ionGenerator.writePOJO(value.getObjectField());
            ionGenerator.writeEndSexp();
        }
    }
}
