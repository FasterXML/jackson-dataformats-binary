package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;

// for [dataformats-binary#43]
public class SharedRawGeneratorBufferTest extends CBORTestBase
{
    public static class RawBean {
        public String value;

        public RawBean(String value) {
            this.value = value;
        }

        @JsonValue
        @JsonRawValue
        public String getValue() {
            return value;
        }
    }

    public void testSharedBuffersWithRaw() throws Exception
    {
        String data = "{\"x\":\"" + generate(5000) + "\"}";
        RawBean bean = new RawBean(data);
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = new JsonFactory(mapper);
        CBORFactory cborFactory = new CBORFactory(mapper);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        
        CBORGenerator cborGen = cborFactory.createGenerator(bytes);
        cborGen.writeObject(1);
        cborGen.close();
        bytes.reset();

        JsonGenerator jsonGen = factory.createGenerator(bytes);
        jsonGen.writeObject(bean);
        jsonGen.close();

        // should not fail, that's all
    }

    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String generate(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }    
}
