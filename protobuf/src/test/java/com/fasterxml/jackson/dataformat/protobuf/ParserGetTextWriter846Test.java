package com.fasterxml.jackson.dataformat.protobuf;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#846]: getText(Writer) wrote stale text buffer contents
// for numeric tokens, and threw NPE for binary values
public class ParserGetTextWriter846Test extends ProtobufTestBase
{
    final protected static String PROTOC_BEAN =
            "message Bean {\n"
            +" optional string s = 1;\n"
            +" optional int32 i = 2;\n"
            +" optional int64 l = 3;\n"
            +" optional float f = 4;\n"
            +" optional double d = 5;\n"
            +" optional bytes b = 6;\n"
            +" optional bool t = 7;\n"
            +" optional bool fl = 8;\n"
            +" optional StdEnum se = 9;\n"
            +" optional SparseEnum xe = 10;\n"
            +"}\n"
            // indexes 0..N-1: exposed as VALUE_NUMBER_INT
            +"enum StdEnum {\n"
            +" A = 0;\n"
            +" B = 1;\n"
            +" C = 2;\n"
            +"}\n"
            // non-standard indexes: exposed as VALUE_STRING
            +"enum SparseEnum {\n"
            +" X = 1;\n"
            +" Y = 5;\n"
            +"}\n"
    ;

    enum StdEnum { A, B, C; }

    enum SparseEnum { X, Y; }

    public static class Bean {
        public String s = "abcdefghijklmnopqrstuvwxyz0123456789";
        public int i = 42;
        public long l = -1234567890123L;
        public float f = 1.25f;
        public double d = 1.25;
        public byte[] b = new byte[] { 1, 2, 3 };
        public boolean t = true;
        public boolean fl = false;
        public StdEnum se = StdEnum.C;
        public SparseEnum xe = SparseEnum.Y;
    }

    private final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testGetTextWriter() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BEAN);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(new Bean());

        try (JsonParser p = MAPPER.getFactory().createParser(doc)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "s");
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            _verifyText(p, "abcdefghijklmnopqrstuvwxyz0123456789");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "i");
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _verifyText(p, "42");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "l");
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _verifyText(p, "-1234567890123");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "f");
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            _verifyText(p, "1.25");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "d");
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            _verifyText(p, "1.25");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "b");
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            assertNull(p.getText());
            StringWriter w = new StringWriter();
            assertEquals(0, p.getText(w));
            assertEquals("", w.toString());
            assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "t");
            assertToken(JsonToken.VALUE_TRUE, p.nextToken());
            _verifyText(p, "true");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "fl");
            assertToken(JsonToken.VALUE_FALSE, p.nextToken());
            _verifyText(p, "false");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "se");
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _verifyText(p, "2");

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _verifyText(p, "xe");
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            _verifyText(p, "Y");

            assertToken(JsonToken.END_OBJECT, p.nextToken());
            _verifyText(p, "}");
            assertNull(p.nextToken());
        }
    }

    private void _verifyText(JsonParser p, String exp) throws Exception
    {
        assertEquals(exp, p.getText());
        StringWriter w = new StringWriter();
        assertEquals(exp.length(), p.getText(w));
        assertEquals(exp, w.toString());
    }
}
