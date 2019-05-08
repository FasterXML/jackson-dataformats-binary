package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;

import com.fasterxml.jackson.databind.JsonNode;

// for [dataformat-cbor#13]
public class ParserInputStreamTest extends CBORTestBase
{
    public void testInpuStream() throws Exception
    {
        byte[] buffer = generateHugeCBOR();

        // split the buffer in two smaller buffer
        int len = 160;
        byte[] buf1 = new byte[len];
        byte[] buf2 = new byte[buffer.length - len];
        System.arraycopy(buffer, 0, buf1, 0, len);
        System.arraycopy(buffer, len, buf2, 0, buffer.length - len);

        // aggregate the two buffers via a SequenceInputStream
        ByteArrayInputStream in1 = new ByteArrayInputStream(buf1);
        ByteArrayInputStream in2 = new ByteArrayInputStream(buf2);
        SequenceInputStream inputStream = new SequenceInputStream(in1, in2);

        JsonNode jsonNode = sharedMapper().readTree(inputStream);
        assertNotNull(jsonNode);
    }

    private byte[] generateHugeCBOR() throws IOException {
        String hugeJson = "{";
        for (char c='a'; c <= 'z'; c++) {
            for (char cc='a'; cc <= 'z'; cc++) {
                hugeJson += "\"" + c + cc + "\":0,";
            }
            for (int i = 0; i < 50; i++) {
                hugeJson += "\"" + c + i + "\":" + i + ",";
            }
        }
        hugeJson += "\"name\":123";
        hugeJson += "}";
        return cborDoc(hugeJson);
    }
}
