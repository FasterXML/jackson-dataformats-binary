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

import org.junit.Test;
import org.junit.Before;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonGenerator;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import software.amazon.ion.IonDatagram;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonStruct;
import software.amazon.ion.IonSystem;
import software.amazon.ion.system.IonSystemBuilder;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class IonGeneratorTest {
    private static final Map<String, String> testObject;
    private static final String testObjectStr =
        "{\n" +
        "    a: \"A\",\n" +
        "    b: \"B\",\n" +
        "    c: \"C\",\n" +
        "}";

    static {
        final Map<String, String> map = new HashMap<String, String>();
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

    @Before
    public void setUp() throws Exception {
        final IonFactory factory = new IonFactory(); 

        this.joiObjectMapper = new IonObjectMapper(factory);
        this.ionSystem = IonSystemBuilder.standard().build();
        this.output = ionSystem.newDatagram();
        this.joiGenerator = (IonGenerator) factory.createGenerator(ionSystem.newWriter(this.output));

        this.testObjectIon = ionSystem.singleValue(testObjectStr);
        this.testObjectTree = joiObjectMapper.readTree(testObjectStr);
    }

    @Test
    public void testSimpleWrite() throws Exception {
        joiGenerator.writeBoolean(true);
        assertThat(output.get(0), is((IonValue)ionSystem.newBool(true)));
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

        final IonStruct struct = (IonStruct) output.get(0);
        assertThat(struct.get(FIELD), is(testObjectIon));
    }
}
