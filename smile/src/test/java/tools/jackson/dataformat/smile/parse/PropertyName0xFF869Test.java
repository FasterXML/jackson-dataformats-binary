package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#869]: name with 0xFF byte at start of last quad must not
// match a shorter name already in the symbol table
public class PropertyName0xFF869Test extends BaseTestForSmile
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    private final static int[] NAME_LENGTHS = {
        1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15, 17, 18, 19, 21, 22, 23,
        // long names
        65, 66, 67, 69, 70, 71
    };

    // Bytes fed per call to non-blocking parser; 0 for blocking from byte[],
    // -1 for blocking from InputStream
    private final static int[] MODES = { 0, -1, 1, 3, 1000 };

    // How names are encoded: short name tokens (ASCII for all-ASCII names,
    // Unicode otherwise); short ASCII tokens even for names with 0xFF; long tokens
    private final static int TOKENS_SHORT = 0;
    private final static int TOKENS_ASCII = 1;
    private final static int TOKENS_LONG = 2;

    private final static int[] TOKEN_TYPES = { TOKENS_SHORT, TOKENS_ASCII, TOKENS_LONG };

    @Test
    public void testSameDocument() throws Exception
    {
        for (int len : NAME_LENGTHS) {
            for (int tokens : TOKEN_TYPES) {
                final byte[] doc = _objectDoc(tokens, _name(len), _nameWith0xFF(len));
                for (int mode : MODES) {
                    _verifyFails(new SmileFactory(), doc, tokens, mode, len);
                }
            }
        }
    }

    @Test
    public void testNameFromEarlierDocument() throws Exception
    {
        for (int len : NAME_LENGTHS) {
            for (int tokens : TOKEN_TYPES) {
                for (int mode : MODES) {
                    final SmileFactory f = new SmileFactory();
                    final byte[] doc1 = _objectDoc(tokens, _name(len));
                    assertEquals(new String(_name(len), "UTF-8"), _names(f, doc1, mode));
                    _verifyFails(f, _objectDoc(tokens, _nameWith0xFF(len)), tokens, mode, len);
                }
            }
        }
    }

    // Name over `maxNameLength` must be reported as such, even if its last quad
    // also starts with 0xFF
    @Test
    public void testTooLongNameWith0xFF() throws Exception
    {
        final int maxLen = 100;
        final SmileFactory f = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNameLength(maxLen)
                        .build())
                .build();
        // names of 102 - 104 bytes: last quad partial (padded) or full
        for (int len : new int[] { maxLen + 1, maxLen + 2, maxLen + 3 }) {
            final byte[] doc = _objectDoc(TOKENS_LONG, _nameWith0xFF(len));
            for (int mode : MODES) {
                try {
                    String names = _names(f, doc, mode);
                    fail("Should have failed for name length "+(len+1)+", mode "+mode+"; got names: "+names);
                } catch (StreamConstraintsException e) {
                    verifyException(e, "Name length");
                }
            }
        }
    }

    private void _verifyFails(SmileFactory f, byte[] doc, int tokens, int mode, int len) throws Exception
    {
        try {
            String names = _names(f, doc, mode);
            fail("Should have failed for name length "+(len+1)+", tokens "+tokens+", mode "+mode+"; got names: "+names);
        } catch (StreamReadException e) {
            verifyException(e, "Invalid UTF-8 start byte 0xff");
        }
    }

    // Reads all property names, comma-separated
    private String _names(SmileFactory f, byte[] doc, int mode) throws Exception
    {
        final StringBuilder sb = new StringBuilder();
        final ObjectReader r = new SmileMapper(f).reader();
        if (mode <= 0) {
            try (JsonParser p = (mode == 0) ? r.createParser(doc)
                    : r.createParser(new ByteArrayInputStream(doc))) {
                JsonToken t;
                while ((t = p.nextToken()) != null) {
                    if (t == JsonToken.PROPERTY_NAME) {
                        _append(sb, p.currentName());
                    }
                }
            }
            return sb.toString();
        }
        try (JsonParser p = r.createNonBlockingByteArrayParser()) {
            final ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            int offset = 0;
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.NOT_AVAILABLE) {
                    if (offset < doc.length) {
                        int end = Math.min(doc.length, offset + mode);
                        feeder.feedInput(doc, offset, end);
                        offset = end;
                    } else {
                        feeder.endOfInput();
                    }
                } else if (t == JsonToken.PROPERTY_NAME) {
                    _append(sb, p.currentName());
                }
            }
        }
        return sb.toString();
    }

    private static void _append(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(',');
        }
        sb.append(name);
    }

    // ASCII name of given length
    private static byte[] _name(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; ++i) {
            b[i] = (byte) ('a' + (i % 26));
        }
        return b;
    }

    // Name one byte longer than `_name(len)`, with 0xFF inserted at start of
    // its last quad, so that its quads equal those of `_name(len)` once padded
    // (`len` must not be a multiple of 4)
    private static byte[] _nameWith0xFF(int len) {
        final byte[] name = _name(len);
        final int lastQuadStart = (len >> 2) << 2;
        byte[] b = new byte[len+1];
        System.arraycopy(name, 0, b, 0, lastQuadStart);
        b[lastQuadStart] = (byte) 0xFF;
        System.arraycopy(name, lastQuadStart, b, lastQuadStart+1, len - lastQuadStart);
        return b;
    }

    // Object with given property names (with small int values), using name
    // tokens of given type (long tokens for names too long for short ones)
    private static byte[] _objectDoc(int tokens, byte[]... names) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER, 0, HEADER.length);
        out.write(SmileConstants.TOKEN_LITERAL_START_OBJECT);
        for (byte[] name : names) {
            final int len = name.length;
            final boolean ascii = (tokens == TOKENS_ASCII) || _isAscii(name);
            if (tokens == TOKENS_LONG || len > (ascii ? 64 : 56)) {
                out.write(SmileConstants.TOKEN_KEY_LONG_STRING);
                out.write(name, 0, len);
                out.write(SmileConstants.BYTE_MARKER_END_OF_STRING);
            } else if (ascii) {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_ASCII + len - 1);
                out.write(name, 0, len);
            } else {
                out.write(SmileConstants.TOKEN_PREFIX_KEY_UNICODE + len - 2);
                out.write(name, 0, len);
            }
            out.write(0xC2); // small int 1
        }
        out.write(SmileConstants.TOKEN_LITERAL_END_OBJECT);
        return out.toByteArray();
    }

    private static boolean _isAscii(byte[] b) {
        for (byte x : b) {
            if (x < 0) {
                return false;
            }
        }
        return true;
    }
}
