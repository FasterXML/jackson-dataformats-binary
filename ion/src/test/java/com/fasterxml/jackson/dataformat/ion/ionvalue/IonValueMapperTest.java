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

package com.fasterxml.jackson.dataformat.ion.ionvalue;

import java.io.IOException;
import java.util.*;

import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.IonSymbolSerializer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test of the {@link IonValueMapper} parser.
 */
public class IonValueMapperTest {
    private final IonSystem ionSystem = IonSystemBuilder.standard().build();
    private final IonValueMapper ionValueMapper = (IonValueMapper) IonValueMapper.builder(ionSystem,
            PropertyNamingStrategies.SNAKE_CASE).build();

    enum ReturnCode {
        Success,
        FileNotFound
    }

    public static class TestPojo1 {
        public String myString;
        @JsonSerialize(using = IonSymbolSerializer.class)
        public String mySymbol;
        public boolean doesThisWork;
        @JsonProperty("some_other_name")
        public int iHaveSomeOtherName;
        public ReturnCode imAnEnum;
        public Timestamp someTime;
    }

    @Test
    public void testMapperCopy() throws Exception {
        ObjectMapper mapper2 = ionValueMapper.copy();
        assertNotSame(ionValueMapper, mapper2);
    }

    @Test
    public void testNull() throws Exception {
        assertNull(ionValueMapper.parse(null, TestPojo1.class));
        assertNull(ionValueMapper.serialize(null));
    }

    @Test
    public void testPojo1() throws Exception {
        String value = "{" +
                "my_string:\"yes\"," +
                "my_symbol:yes," +
                "does_this_work:false," +
                "some_other_name:5," +
                "im_an_enum:Success," +
                "some_time:2010-01-01T06:00:00Z" +
                "}";

        TestPojo1 t = ionValueMapper.parse(ionSystem.singleValue(value), TestPojo1.class);
        assertEquals("yes", t.myString);
        assertEquals("yes", t.mySymbol);
        assertFalse(t.doesThisWork);
        assertEquals(5, t.iHaveSomeOtherName);
        assertEquals(ReturnCode.Success, t.imAnEnum);
        assertEquals(Timestamp.valueOf("2010-01-01T06:00:00Z"), t.someTime);

        assertRoundTrip(value, TestPojo1.class);
    }

    public static class SexpWrapper {
        IonSexp sexp;

        @JsonCreator
        public SexpWrapper (@JsonProperty("sexp") IonSexp sexp) {
            this.sexp = sexp;
        }
        public IonSexp getSexp() {
            return sexp;
        }
    }

    public static class TestPojo2 {
        public IonValue rawValue;
        public IonSexp rawSexp;
        public SexpWrapper wrappedSexp;
    }

    @Test
    public void testPojo2() throws Exception {
        String value = "{" +
                "raw_value:{this:that}," +
                "raw_sexp:(this that)," +
                "wrapped_sexp:{sexp:(other)}," +
                "}";

        TestPojo2 t = ionValueMapper.parse(ionSystem.singleValue(value), TestPojo2.class);
        assertEquals(ionSystem.singleValue("{this:that}"), t.rawValue);
        assertEquals(ionSystem.singleValue("(this that)"), t.rawSexp);
        assertEquals(ionSystem.singleValue("(other)"), t.wrappedSexp.sexp);

        assertRoundTrip(value, TestPojo2.class);
    }

    @Test
    public void testPojo2WithTimestamp() throws Exception {
        String value = "{" +
                "raw_value:2017-05-25T15:33:08Z," +
                "raw_sexp:(this that)," +
                "wrapped_sexp:{sexp:(other)}," +
                "}";

        TestPojo2 t = ionValueMapper.parse(ionSystem.singleValue(value), TestPojo2.class);
        assertEquals(ionSystem.singleValue("2017-05-25T15:33:08Z"), t.rawValue);
        assertEquals(ionSystem.singleValue("(this that)"), t.rawSexp);
        assertEquals(ionSystem.singleValue("(other)"), t.wrappedSexp.sexp);

        assertRoundTrip(value, TestPojo2.class);
    }

    @Test
    public void testPojo2WithBlob() throws Exception {
        String value = "{" +
                "raw_value:{{YmxvYl92YWx1ZQ==}}," +
                "raw_sexp:(this that)," +
                "wrapped_sexp:{sexp:(other)}," +
                "}";

        TestPojo2 t = ionValueMapper.parse(ionSystem.singleValue(value), TestPojo2.class);
        assertEquals(ionSystem.newBlob("blob_value".getBytes()), t.rawValue);
        assertEquals(ionSystem.singleValue("(this that)"), t.rawSexp);
        assertEquals(ionSystem.singleValue("(other)"), t.wrappedSexp.sexp);

        assertRoundTrip(value, TestPojo2.class);
    }

    /**
     * This Pojo supports open content
     */
    public static class TestPojo3 {
        public int expected;

        protected Map<String, IonValue> other = new HashMap<>();

        // "any getter" needed for serialization
        @JsonAnyGetter
        public Map<String, IonValue> any() {
            return other;
        }

        @JsonAnySetter
        public void set(String name, IonValue value) {
            other.put(name, value);
        }
    }

    @Test
    public void testPojo3WithOpenContent() throws Exception {
        String value = "{" +
                "expected:1," +
                "something_unexpected:(boo!)," +
                "another_random_struct:{yikes:scared}," +
                "timestamp_att:2021-02-15T18:40:40Z," +
                "blob_att:{{YmxvYl92YWx1ZQ==}}," +
                "}";

        TestPojo3 t = ionValueMapper.parse(ionSystem.singleValue(value), TestPojo3.class);
        assertEquals(1, t.expected);
        assertEquals(ionSystem.singleValue("(boo!)"), t.any().get("something_unexpected"));
        assertEquals(ionSystem.singleValue("{yikes:scared}"), t.any().get("another_random_struct"));
        assertEquals(ionSystem.singleValue("2021-02-15T18:40:40Z"), t.any().get("timestamp_att"));
        assertEquals(ionSystem.newBlob("blob_value".getBytes()), t.any().get("blob_att"));

        assertRoundTrip(value, TestPojo3.class);
    }

    public static class TestPojo4 {
        public String number;
    }

    @Test
    public void testPojo4WithSexpInArrayIgnored() throws Exception {
        IonObjectMapper mapper = new IonValueMapper(ionSystem);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        String value = "{value:[([])], number:\"Random\"}";
        TestPojo4 test = mapper.readValue(ionSystem.singleValue(value), TestPojo4.class);
        assertNotNull(test);
        assertNotNull(test.number);
        assertEquals("Random", test.number);
    }


    public static class TestPojo5 {
        public String number;
        public List<IonSexp> value;
    }

    @Test
    public void testPojo5WithSexpInArray() throws Exception {
        IonObjectMapper mapper = new IonValueMapper(ionSystem);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        String value = "{value:[([blah])], number:\"Random\"}";
        TestPojo5 test = mapper.readValue(ionSystem.singleValue(value), TestPojo5.class);
        assertNotNull(test);
        assertNotNull(test.number);
        assertEquals("Random", test.number);
        assertNotNull(test.value.get(0));

        assertRoundTrip(value, TestPojo5.class);
    }

    private void assertRoundTrip(String ion, Class<?> clazz) throws IOException {
        IonValue expected = ionSystem.singleValue(ion);
        Object o = ionValueMapper.parse(expected, clazz);
        IonValue actual = ionValueMapper.serialize(o);
        assertEquals(expected, actual);
    }

    // for [dataformats-binary#509]
    @Test
    public void testBuilders() throws Exception {
        IonObjectMapper mapper = IonValueMapper.builder().build();
        assertNotNull(mapper);
        assertEquals(IonValueMapper.class, mapper.getClass());

        mapper = IonValueMapper.builder(ionSystem).build();
        assertNotNull(mapper);
        assertEquals(IonValueMapper.class, mapper.getClass());
    }
}
