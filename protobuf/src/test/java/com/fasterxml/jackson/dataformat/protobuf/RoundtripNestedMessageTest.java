package com.fasterxml.jackson.dataformat.protobuf;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

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

    @Test
    public void testNestedRoundtrip() throws IOException
    {
       TestObject testClass = new TestObject();
       ProtobufMapper om = new ProtobufMapper();
       ProtobufSchema s = ProtobufSchemaLoader.std.parse(PROTO);
       testClass.a = VALUE_A;
       testClass.b = new TestSub();
       testClass.b.b = VALUE_B;
       testClass.b.c = VALUE_C;
       // if this following row is commented out, test succeeds with old code
       testClass.b.d = new TestSubSub();
       testClass.b.d.a = VALUE_SUB_A;

       byte[] proto = om.writer(s)
               .writeValueAsBytes(testClass);
        TestObject res = om.readerFor(TestObject.class).with(s)
               .readValue(proto);

       Assert.assertEquals(VALUE_A, res.a);
       Assert.assertEquals(VALUE_C, res.b.c);
       Assert.assertEquals(VALUE_B, res.b.b);
       Assert.assertEquals(VALUE_SUB_A, res.b.d.a);
    }
}
