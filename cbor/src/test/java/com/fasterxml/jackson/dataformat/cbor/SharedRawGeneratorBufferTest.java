package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.*;
import tools.jackson.databind.ObjectMapper;

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

        ObjectMapper cborMapper = cborMapper();
        ObjectMapper jsonMapper = new ObjectMapper();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        
        CBORGenerator cborGen = (CBORGenerator) cborMapper.createGenerator(bytes);
        cborGen.writePOJO(1);
        cborGen.close();
        bytes.reset();

        JsonGenerator jsonGen = jsonMapper.createGenerator(bytes);
        jsonGen.writePOJO(bean);
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
