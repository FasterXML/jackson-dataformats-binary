package tools.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

import tools.jackson.core.JsonLocation;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ParserInputStreamTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // for [dataformat-cbor#13]
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

        JsonNode jsonNode = MAPPER.readTree(inputStream);
        assertNotNull(jsonNode);
    }

    public void testInputStreamWithHugeValueThatOverlaps() throws Exception {
        final byte[] buffer = new byte[8002];
        buffer[0] = 0x79; // string length 7996 + 3 init bytes
        buffer[1] = 0x1f;
        buffer[2] = 0x3c;
        buffer[7999] = 0x61; // string length 1 + 1 init byte

        final InputStream in = new ByteArrayInputStream(buffer);
        try (final JsonParser parser = MAPPER.createParser(in)) {
            parser.nextToken();
            parser.finishToken();
    
            final JsonLocation loc = parser.currentLocation();
            final long start = loc.getByteOffset();
            assertEquals(7999, start);
    
            assertEquals("byte offset: #7999", loc.offsetDescription());
            assertEquals("(ByteArrayInputStream)", loc.sourceDescription());

            parser.nextToken();
            parser.finishToken();
    
            final long end = parser.currentLocation().getByteOffset();
            assertEquals(8001, end);
        }
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
