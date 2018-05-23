package com.fasterxml.jackson.dataformat.protobuf;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class RoundtripNestedMessageTest extends ProtobufTestBase
{
    private static final String VALUE_A = "value";
    private static final String VALUE_B = "value-b";
    private static final String VALUE_C = "valc";
    private static final String VALUE_SUB_A = "a-value!";

    private static final String PROTO = //
            "message TestObject {\n" + //
                      "optional string a = 1;\n" + //
                      "optional TestSub b = 2;\n" + //
                      "}\n" + //
                      "message TestSub {;\n" + //
                      "optional string c = 2;\n" + //
                      "optional string b = 3;\n" + //
                      "optional TestSubSub d = 4;\n" + //
                      "}\n" + //
                      "message TestSubSub {;\n" + //
                      "optional string a = 1;\n" + //
                      "}\n"; //

    static class TestObject {
       String a;
       TestSub b;

       public String getA() {
            return a;
       }

       public void setA(String a) {
            this.a = a;
       }

       public TestSub getB() {
            return b;
       }

       public void setB(TestSub b) {
            this.b = b;
       }
    }

    // ordering would be needed prior to fix for [#59]
    //@com.fasterxml.jackson.annotation.JsonPropertyOrder({"d", "b", "c"})
    static class TestSub {
        String b;
        String c;
        TestSubSub d;

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        public TestSubSub getD() {
            return d;
        }

        public void setD(TestSubSub d) {
            this.d = d;
        }
    }

    public static class TestSubSub {
        String a;
        
        public String getA() {
            return a;
        }
        
        public void setA(String a) {
            this.a = a;
        }
    }

    // [dataformats-binary#135]: endless END_OBJECT at end of doc
    @JsonPropertyOrder({ "name", "age", "emails", "boss" })
    static class Employee135 {
        public int age;
 
        public String[] emails;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ProtobufMapper MAPPER = new ProtobufMapper();

    public void testNestedRoundtrip() throws IOException
    {
       TestObject testClass = new TestObject();
       ProtobufSchema s = ProtobufSchemaLoader.std.parse(PROTO);
       testClass.a = VALUE_A;
       testClass.b = new TestSub();
       testClass.b.b = VALUE_B;
       testClass.b.c = VALUE_C;
       // if this following row is commented out, test succeeds with old code
       testClass.b.d = new TestSubSub();
       testClass.b.d.a = VALUE_SUB_A;

       byte[] proto = MAPPER.writer(s)
               .writeValueAsBytes(testClass);
        TestObject res = MAPPER.readerFor(TestObject.class).with(s)
               .readValue(proto);

       assertEquals(VALUE_A, res.a);
       assertEquals(VALUE_C, res.b.c);
       assertEquals(VALUE_B, res.b.b);
       assertEquals(VALUE_SUB_A, res.b.d.a);
    }

    // [dataformats-binary#135]: endless END_OBJECT at end of doc
    public void testIssue135() throws Exception
    {
        String protobuf_str = "message Employee {\n"
                + " required int32 age = 1;\n"
                + " repeated string emails = 3;\n"
                + "}\n";
        final ProtobufSchema schema = MAPPER.schemaLoader().parse(protobuf_str);

        Employee135 empl = new Employee135();
        empl.age = 30;
        empl.emails = new String[]{"foo@gmail.com"};

        byte[] protobufData = MAPPER.writer(schema)
                .writeValueAsBytes(empl);

        JsonParser p = new ProtobufFactory().createParser(protobufData);
        p.setSchema(schema);

        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("age", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(30, p.getIntValue());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("emails", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo@gmail.com", p.getText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertNull(p.nextToken());
        p.close();
    }
}
