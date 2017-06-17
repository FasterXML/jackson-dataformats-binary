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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.ion.EnumAsIonSymbolSerializer;
import com.fasterxml.jackson.dataformat.ion.IonSymbolSerializer;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import software.amazon.ion.IonStruct;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonValue;
import software.amazon.ion.system.IonSystemBuilder;

/**
 * Simple unit tests to check that write-then-read works as expected
 * for beans.
 */
public class DataBindRoundtripTest
{
    enum TestEnum { A, B, C }

    static class Bean {
        public String a;
        public double b;
        public boolean state;
        public byte[] data;
        public List<String> sexp;
        public List<Object> nestedSexp;
        public SubBean sub;
        @JsonSerialize(using=IonSymbolSerializer.class)
        public String symbol;
        @JsonSerialize(using=EnumAsIonSymbolSerializer.class)
        public TestEnum enumVal;
        public BigInteger bigInt;
        public BigDecimal bigDec;
        public float f;

        public Bean() { this(null, 0.0, false, null, null, null, null, null, null); }
        public Bean(String a, double b, boolean c, byte[] d, List<String> e,
                    SubBean f, String g, TestEnum h, BigInteger bigInt) {
            this.a = a;
            this.b = b;
            this.state = c;
            this.data = d;
            this.sexp = e;
            this.sub = f;
            this.symbol = g;
            this.enumVal = h;
            this.bigInt = bigInt;
            this.bigDec = BigDecimal.valueOf(b);
            this.f = (float) b;
        }
    }

    static class SubBean {
        protected String value;
        
        @JsonCreator
        public SubBean(@JsonProperty("value") String v) {
            value = v;
        }

        public String getValue() { return value; }
    }
    
    @Test
    public void testSimple() throws IOException
    {
        Bean bean = new Bean(
               "test", 
               0.25, 
               true, 
               new byte[] { 1, 2, 3 }, 
               Collections.singletonList("sexpEntry"), 
               new SubBean("yellow"), 
               "testSymbol",
               TestEnum.B,
               BigInteger.valueOf(Integer.MAX_VALUE + 42L));
        doTests(bean, new IonObjectMapper());
    }
    
    @Test
    public void testBigInt() throws IOException
    {
        Bean bean = new Bean();
        bean.bigInt = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        IonObjectMapper mapper = new IonObjectMapper();
        doTests(bean, mapper);
    }
    @Test
    public void testSmallBigInt() throws IOException
    {
        Bean bean = new Bean();
        bean.bigInt = BigInteger.valueOf(42);
        IonObjectMapper mapper = new IonObjectMapper();
        doTests(bean, mapper);
    }
    
    @Test
    public void testNullFields() throws IOException
    {
        doTests(new Bean(), new IonObjectMapper());
    }
    
    private void doTests(Bean bean, IonObjectMapper mapper) throws IOException
    {
        for (RoundTrippers rt : RoundTrippers.values()) {
            try
            {
                _testRoundTrip(bean, rt, mapper);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failure during RoundTrippers."+rt.name(), e);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testIonRoot() throws IOException {
        String stringBean= "{a:'test',b:0.25,state:true,data:{{}}, sexp:(foo bar), nestedSexp:([foo2, foo3] (foo4 foo5)), sub:{value:'yellow'}, symbol:testSymbol, enumVal: B}";
        
        IonSystem system = IonSystemBuilder.standard().build();
        IonValue root = system.newLoader().load(stringBean).iterator().next();
        IonObjectMapper m = new IonObjectMapper();
       
        Bean bean = m.readValue(root, Bean.class);
        assertNotNull(bean);
        assertEquals(bean.a, "test");
        assertTrue(bean.b == 0.25);
        assertArrayEquals(new byte[0], bean.data);
        assertEquals(bean.state, true);
        assertNotNull(bean.sub);
        assertEquals("yellow", bean.sub.getValue());
        assertEquals("testSymbol", bean.symbol);
        assertEquals(TestEnum.B, bean.enumVal);
        assertEquals("foo", bean.sexp.get(0));
        assertEquals("bar", bean.sexp.get(1));
        assertEquals("foo2", ((List) bean.nestedSexp.get(0)).get(0));
        assertEquals("foo3", ((List) bean.nestedSexp.get(0)).get(1));
        assertEquals("foo4", ((List) bean.nestedSexp.get(1)).get(0));
        assertEquals("foo5", ((List) bean.nestedSexp.get(1)).get(1));
        IonValue subRoot = ((IonStruct)root).get("sub");
        subRoot.removeFromContainer();
        SubBean subbean = m.readValue(subRoot, SubBean.class);
        assertNotNull(subbean);
        assertEquals("yellow",subbean.getValue());
        
    }
    
    enum RoundTrippers {
        BINARY {
            @Override
            Bean roundTrip(IonObjectMapper m, Bean bean) throws IOException {
                m.setCreateBinaryWriters(true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                m.writeValue(out, bean);
                byte[] data = out.toByteArray();
                return m.readValue(data, 0, data.length, Bean.class);
            }
        },
        TEXT {
            @Override
            Bean roundTrip(IonObjectMapper m, Bean bean) throws IOException {
                m.setCreateBinaryWriters(false);
                String str = m.writeValueAsString(bean);
                return m.readValue(str, Bean.class);
            }
        },
        ION {
            @Override
            Bean roundTrip(IonObjectMapper m, Bean bean) throws IOException {
                return m.readValue(m.writeValueAsIonValue(bean), Bean.class);
            }
        };
        abstract Bean roundTrip(IonObjectMapper m, Bean bean) throws IOException;
    }

    private void _testRoundTrip(Bean bean, RoundTrippers rt, IonObjectMapper m) throws IOException
    {
        Bean result = rt.roundTrip(m, bean);

        assertNotNull(result);
        assertEquals(bean.a, result.a);
        assertTrue(bean.b == result.b);
        assertArrayEquals(bean.data, result.data);
        assertEquals(bean.state, result.state);
        if (bean.sub == null)
        {
            assertNull(result.sub);
        }
        else
        {
            assertNotNull(result.sub);
            assertEquals(bean.sub.getValue(), result.sub.getValue());
        }
        assertEquals(bean.symbol, result.symbol);
        assertEquals(bean.enumVal, result.enumVal);
        assertEquals(bean.bigDec, result.bigDec);
        assertEquals(bean.bigInt, result.bigInt);
        assertTrue(bean.f == result.f);
    }
}
