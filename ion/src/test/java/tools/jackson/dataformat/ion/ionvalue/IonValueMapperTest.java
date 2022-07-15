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

package tools.jackson.dataformat.ion.ionvalue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.*;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.dataformat.ion.IonFactory;
import tools.jackson.dataformat.ion.IonObjectMapper;
import tools.jackson.dataformat.ion.IonSymbolSerializer;

import com.amazon.ion.IonSexp;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.Timestamp;
import com.amazon.ion.system.IonSystemBuilder;

public class IonValueMapperTest {
    private final IonSystem ionSystem = IonSystemBuilder.standard().build();
    private final IonFactory ionF = IonFactory.builderForTextualWriters().ionSystem(ionSystem).build();
    private final IonObjectMapper ionValueMapper = IonObjectMapper.builder(ionF)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

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

    /**
     * This Pojo supports open content
     */
    public static class TestPojo3 {
        public int expected;

        protected Map<String, IonValue> other = new HashMap<String, IonValue>();

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

    public static class TestPojo4 {
        public String number;
    }

    public static class TestPojo5 {
        public String number;
        public List<IonSexp> value;
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */
    
    @Test
    public void testNull() throws Exception {
        assertNull(ionValueMapper.readValue((IonValue) null, TestPojo1.class));

        // 04-Jan-2017, tatu: Bit of incompatiblity wrt 2.x handling: should this result in
        //   Java `null`, or Ion null marker? For now, choose latter
//        assertNull(ionValueMapper.writeValueAsIonValue(null));
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

        TestPojo1 t = ionValueMapper.readValue(ionSystem.singleValue(value), TestPojo1.class);
        assertEquals("yes", t.myString);
        assertEquals("yes", t.mySymbol);
        assertEquals(false, t.doesThisWork);
        assertEquals(5, t.iHaveSomeOtherName);
        assertEquals(ReturnCode.Success, t.imAnEnum);
        assertEquals(Timestamp.valueOf("2010-01-01T06:00:00Z"), t.someTime);

        assertRoundTrip(value, TestPojo1.class);
    }

    @Test
    public void testPojo2() throws Exception {
        String value = "{" +
                "raw_value:{this:that}," +
                "raw_sexp:(this that)," +
                "wrapped_sexp:{sexp:(other)}," +
                "}";

        TestPojo2 t = ionValueMapper.readValue(ionSystem.singleValue(value), TestPojo2.class);
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

        TestPojo2 t = ionValueMapper.readValue(ionSystem.singleValue(value), TestPojo2.class);
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

        TestPojo2 t = ionValueMapper.readValue(ionSystem.singleValue(value), TestPojo2.class);
        assertEquals(ionSystem.newBlob("blob_value".getBytes()), t.rawValue);
        assertEquals(ionSystem.singleValue("(this that)"), t.rawSexp);
        assertEquals(ionSystem.singleValue("(other)"), t.wrappedSexp.sexp);

        assertRoundTrip(value, TestPojo2.class);
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

        TestPojo3 t = ionValueMapper.readValue(ionSystem.singleValue(value), TestPojo3.class);
        assertEquals(1, t.expected);
        assertEquals(ionSystem.singleValue("(boo!)"), t.any().get("something_unexpected"));
        assertEquals(ionSystem.singleValue("{yikes:scared}"), t.any().get("another_random_struct"));
        assertEquals(ionSystem.singleValue("2021-02-15T18:40:40Z"), t.any().get("timestamp_att"));
        assertEquals(ionSystem.newBlob("blob_value".getBytes()), t.any().get("blob_att"));

        assertRoundTrip(value, TestPojo3.class);
    }

    @Test
    public void testPojo4WithSexpInArrayIgnored() throws Exception {
        IonObjectMapper mapper = IonObjectMapper.builder(ionF)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        String value = "{value:[([])], number:\"Random\"}";
        TestPojo4 test = mapper.readValue(ionSystem.singleValue(value), TestPojo4.class);
        assertNotNull(test);
        assertNotNull(test.number);
        assertEquals("Random", test.number);
    }

    @Test
    public void testPojo5WithSexpInArray() throws Exception {
        IonObjectMapper mapper = IonObjectMapper.builder(ionF)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

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
        Object o = ionValueMapper.readValue(expected, clazz);
        IonValue actual = ionValueMapper.writeValueAsIonValue(o);
        assertEquals(expected, actual);
    }
}
