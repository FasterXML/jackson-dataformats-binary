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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonNode;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class IonGeneratorTest {
    private static final Map<String, String> testObject;
    private static final String testObjectStr =
        "{\n" +
        "    a: \"A\",\n" +
        "    b: \"B\",\n" +
        "    c: \"C\",\n" +
        "}";

    static {
        final Map<String, String> map = new HashMap<>();
        map.put("a", "A");
        map.put("b", "B");
        map.put("c", "C");
        testObject = Collections.unmodifiableMap(map);
    }

    private IonSystem ionSystem;
    private IonObjectMapper joiObjectMapper;
    private IonGenerator joiGenerator;

    private IonDatagram output;
    private IonValue testObjectIon;
    private JsonNode testObjectTree;

    @BeforeEach
    public void setUp() throws Exception {
        final IonFactory factory = new IonFactory();

        this.joiObjectMapper = IonObjectMapper.builder(factory).build();
        this.ionSystem = IonSystemBuilder.standard().build();
        this.output = ionSystem.newDatagram();
        this.joiGenerator = (IonGenerator) factory.createGenerator(ionSystem.newWriter(this.output));

        this.testObjectIon = ionSystem.singleValue(testObjectStr);
        this.testObjectTree = joiObjectMapper.readTree(testObjectStr);
    }

    @Test
    public void testSimpleWrite() throws Exception {
        joiGenerator.writeBoolean(true);
        assertThat(output.get(0), is(ionSystem.newBool(true)));
    }

    @Test
    public void testObjectWrite() throws Exception {
        joiGenerator.writeObject(testObject);
        assertThat(output.get(0), is(testObjectIon));
    }

    @Test
    public void testTreeWrite() throws Exception {
        joiGenerator.writeTree(testObjectTree);
        assertThat(output.get(0), is(testObjectIon));
    }

    @Test
    public void testObjectWriteVerifiesOnce() throws Exception {
        final String FIELD = "field";
        // We can test this by writing into a context where only a single object can be written
        joiGenerator.writeStartObject();
        joiGenerator.writeFieldName(FIELD);
        joiGenerator.writeObject(testObject);
        joiGenerator.writeEndObject();


        final IonStruct struct = (IonStruct) output.get(0);
        assertThat(struct.get(FIELD), is(testObjectIon));
    }

    @Test
    public void testTreeWriteVerifiesOnce() throws Exception {
        final String FIELD = "field";
        // We can test this by writing into a context where only a single object can be written
        joiGenerator.writeStartObject();
        joiGenerator.writeFieldName(FIELD);
        joiGenerator.writeTree(testObjectTree);
        joiGenerator.writeEndObject();

        // to try to trigger [dataformats-binary#248]
        joiGenerator.close();
        joiGenerator.close();

        final IonStruct struct = (IonStruct) output.get(0);
        assertThat(struct.get(FIELD), is(testObjectIon));
    }

    @Test
    public void testWriteFieldNameFailsInSexp() throws Exception {
        joiGenerator.writeStartSexp();
        try {
            joiGenerator.writeFieldName("foo");
            fail("Should not pass");
        } catch (JsonGenerationException e) {
            assertEquals("Can not write a field name, expecting a value",
                    e.getMessage());
        }
    }

    @Test
    public void testWriteStartSexpFailsWithoutWriteFieldName() throws Exception {
        joiGenerator.writeStartObject();
        try {
            joiGenerator.writeStartSexp();
            fail("Should not pass");
        } catch (JsonGenerationException e) {
            assertEquals("Can not start a sexp, expecting field name",
                    e.getMessage());
        }
    }
}
