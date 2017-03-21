package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class BrokenSurrogatePair {
    @Test
    public void brokenSurrogatePair() throws IOException {
        // The string '{"val":"conclu<d92f> e"}' written as bytes to avoid any encoding issues, etc
        byte[] bytes = {123, 34, 100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 34, 58, 34, 99, 111, 110, 99, 108, 117, -19, -92, -81, 32, 101, 34, 125, 10};
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readValue(bytes, JsonNode.class);

        ObjectMapper smileMapper = new ObjectMapper(new SmileFactory());
        SequenceWriter sequenceWriter = smileMapper.writerFor(JsonNode.class)
                                                   .writeValuesAsArray(new File("./temp.txt"));
        sequenceWriter.write(response);
    }
}
