package tools.jackson.dataformat.ion.fuzz;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.ion.IonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// Corrupt content with a non-null Symbol value for which `IonReader` has no text:
// must be reported as corrupt content, not exposed as `null` String value
// (which would cause NPE in databind)
public class IonFuzzNullSymbolTextTest
{
    private final ObjectMapper ION_MAPPER = new IonObjectMapper();

    private final static byte[] DOC_1 = {
        (byte)0xe0,(byte)0x01,(byte)0x00,(byte)0xea,(byte)0x7d,(byte)0xd0,(byte)0x2c,
        (byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0x00,(byte)0xd0,
        (byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0x00,(byte)0x00,(byte)0xff,(byte)0x19,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x2c,(byte)0xd0,(byte)0xd0,(byte)0xd0,
        (byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0xd0,(byte)0x2e,
        (byte)0x00,(byte)0x98,(byte)0xde
    };

    private final static byte[] DOC_2 = {
        (byte)0xe0,(byte)0x01,(byte)0x00,(byte)0xea,(byte)0x76,(byte)0xce,(byte)0x3f,
        (byte)0x9f,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x2a,(byte)0xc0,
        (byte)0xb7,(byte)0xc0,(byte)0xc0,(byte)0xc0,(byte)0xc0,(byte)0xaf,(byte)0xc0,
        (byte)0x2a,(byte)0xc0,(byte)0xc0,(byte)0x00,(byte)0x08,(byte)0xde
    };

    @Test
    public void getStringOnSymbolWithoutText() throws Exception {
        for (byte[] doc : new byte[][] { DOC_1, DOC_2 }) {
            try (JsonParser p = ION_MAPPER.createParser(doc)) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                p.getString();
                fail("Should not pass (invalid content)");
            } catch (StreamReadException e) {
                _verifyCorrupt(e);
            }
        }
    }

    @Test
    public void readMapFromSymbolWithoutText() throws Exception {
        _verifyReadFails(DOC_1, Map.class);
        _verifyReadFails(DOC_2, Map.class);
    }

    @Test
    public void readListFromSymbolWithoutText() throws Exception {
        _verifyReadFails(DOC_1, List.class);
        _verifyReadFails(DOC_2, List.class);
    }

    @Test
    public void readStringFromSymbolWithoutText() throws Exception {
        _verifyReadFails(DOC_1, String.class);
        _verifyReadFails(DOC_2, String.class);
    }

    private void _verifyReadFails(byte[] doc, Class<?> type) throws Exception {
        try {
            ION_MAPPER.readValue(doc, type);
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            _verifyCorrupt(e);
        }
    }

    private void _verifyCorrupt(StreamReadException e) {
        assertThat(e.getMessage(), Matchers.containsString("Corrupt content to decode"));
        assertThat(e.getMessage(), Matchers.containsString("returned `null` text"));
    }
}
