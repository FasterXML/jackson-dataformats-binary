package com.fasterxml.jackson.dataformat.cbor.constraints;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.io.SerializedString;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

// [dataformats-binary#725]: `maxNameLength` was not enforced for any of the
// Object property name variants CBOR supports
public class LongNameCBORReadTest extends CBORTestBase
{
    private final static int MAX_NAME_LEN = 1000;

    private final CBORFactory F_CONSTRAINED = _factory(MAX_NAME_LEN);

    // Second factory with limit low enough that 1-byte length suffix
    // (max 255 bytes) can exceed it
    private final static int TINY_NAME_LEN = 100;

    private final CBORFactory F_TINY = _factory(TINY_NAME_LEN);

    // Third one with limit below the 23 bytes that fit in the type byte itself
    private final static int MINI_NAME_LEN = 10;

    private final CBORFactory F_MINI = _factory(MINI_NAME_LEN);

    // 5-bit length marker values used by test documents; 0 is not a marker but
    // indicates length encoded in the type byte itself (lengths up to 23)
    private final static int LEN_IN_TYPE_BYTE = 0;
    private final static int LEN_1_BYTE_SUFFIX = 24;
    private final static int LEN_2_BYTE_SUFFIX = 25;
    private final static int LEN_4_BYTE_SUFFIX = 26;

    private static CBORFactory _factory(int maxNameLen) {
        return CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNameLength(maxNameLen)
                        .build())
                .build();
    }

    /*
    /**********************************************************
    /* Test methods, definite-length text names
    /**********************************************************
     */

    // Name with 1-byte length suffix (marker 24)
    public void testLongNameWith1ByteLength() throws Exception
    {
        _verifyNameLengthFail(F_TINY, textNameDoc(200, LEN_1_BYTE_SUFFIX), TINY_NAME_LEN);
    }

    // Name with 2-byte length suffix (marker 25)
    public void testLongNameWith2ByteLength() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(5000, LEN_2_BYTE_SUFFIX),
                MAX_NAME_LEN);
    }

    // Name with 4-byte length suffix (marker 26)
    public void testLongNameWith4ByteLength() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(5000, LEN_4_BYTE_SUFFIX),
                MAX_NAME_LEN);
    }

    // Name too long to fit in the input buffer: decoded in segments
    public void testLongNameLongerThanInputBuffer() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(40_000, LEN_4_BYTE_SUFFIX),
                MAX_NAME_LEN);
    }

    // Limit must be checked before content is read, so that a document that
    // merely claims a huge name fails with constraints (and not EOF) exception,
    // without any attempt to buffer the content
    public void testHugeNameLengthWithoutContent() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(100_000_000, LEN_4_BYTE_SUFFIX, 0),
                MAX_NAME_LEN);
    }

    // Same check needed for indefinite-length Objects, which is what
    // `CBORGenerator` actually writes
    public void testLongNameInIndefiniteLengthObject() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED,
                textNameDoc(5000, LEN_2_BYTE_SUFFIX, 5000, true), MAX_NAME_LEN);
    }

    /*
    /**********************************************************
    /* Test methods, chunked (indefinite-length) text names
    /**********************************************************
     */

    // Chunked name exceeds limit only when chunks are added up: must be
    // caught while decoding, not after the whole name is accumulated
    public void testLongChunkedName() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, chunkedTextNameDoc(100, 20), MAX_NAME_LEN);
    }

    /*
    /**********************************************************
    /* Test methods, non-text names
    /**********************************************************
     */

    // CBOR allows Binary ("byte string") names, too
    public void testLongBinaryName() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, binaryNameDoc(2000), MAX_NAME_LEN);
    }

    public void testLongChunkedBinaryName() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, chunkedBinaryNameDoc(100, 20), MAX_NAME_LEN);
    }

    // And an Integer name may be a "stringref" (tag 25) that resolves to a
    // previously decoded String of any length -- including a String value,
    // which is only bound by the (much higher) `maxStringLength`
    public void testLongStringRefName() throws Exception
    {
        // NOTE: reference is the name of the second entry, so first one to skip
        _verifyNameLengthFail(F_CONSTRAINED, stringRefNameDoc(2000), MAX_NAME_LEN, 1);
    }

    /*
    /**********************************************************
    /* Test methods, cases that must NOT fail
    /**********************************************************
     */

    public void testNameAtMaxLength() throws Exception
    {
        _verifyNameLengthPass(F_CONSTRAINED, textNameDoc(MAX_NAME_LEN, LEN_2_BYTE_SUFFIX),
                MAX_NAME_LEN);
    }

    public void testChunkedNameAtMaxLength() throws Exception
    {
        _verifyNameLengthPass(F_CONSTRAINED, chunkedTextNameDoc(100, 10), MAX_NAME_LEN);
    }

    // Stringref name that stays within the limit must work as before
    public void testShortStringRefName() throws Exception
    {
        _verifyNameLengthPass(F_CONSTRAINED, stringRefNameDoc(500), 500, 1);
    }

    // Enforcement is approximate: shortest names, ones with length encoded in
    // the type byte itself (up to 23 bytes), are not checked at all, making 23
    // bytes the effective minimum limit
    public void testShortNameNotValidated() throws Exception
    {
        _verifyNameLengthPass(F_MINI, textNameDoc(23, LEN_IN_TYPE_BYTE), 23);

        // but one byte longer, and there is an explicit length to check
        _verifyNameLengthFail(F_MINI, textNameDoc(24, LEN_1_BYTE_SUFFIX), MINI_NAME_LEN);
    }

    // Enforcement is approximate in one more way: the name-matching fast path of
    // `nextFieldName(SerializableString)` does not check length of a MATCHING
    // name (up to 255 bytes) since it is the one caller asked for, and not
    // attacker-controlled
    public void testFastPathMatchNotValidated() throws Exception
    {
        final int nameLen = 200;
        final byte[] doc = textNameDoc(nameLen, LEN_1_BYTE_SUFFIX);
        final SerializedString name = new SerializedString(_generateName(nameLen));

        // When fully buffered, fast path matches and no check is made
        try (JsonParser p = cborParser(F_TINY, doc, false)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertTrue(p.nextFieldName(name));
            assertEquals(nameLen, p.currentName().length());
            assertEquals(1, p.nextIntValue(-1));
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }

        // But with input that trickles in, fast path cannot be used, and the
        // limit does then get enforced for the very same document
        try (JsonParser p = cborParser(F_TINY, doc, true)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                p.nextFieldName(name);
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "Name length (200) exceeds the maximum allowed (100");
            }
        }
    }

    /*
    /**********************************************************
    /* Test methods, values: must NOT be affected by name limit
    /**********************************************************
     */

    // Name length checks must not leak into decoding of String values, which
    // are bound by `maxStringLength` instead
    public void testLongStringValueNotAffected() throws Exception
    {
        _verifyValueLengthPass(textValueDoc(5000), 5000, false);
    }

    public void testLongChunkedStringValueNotAffected() throws Exception
    {
        _verifyValueLengthPass(chunkedValueDoc(0x60, 100, 20), 2000, false);
    }

    // ... nor into decoding of Binary values, which are not length-bound at all
    public void testLongChunkedBinaryValueNotAffected() throws Exception
    {
        _verifyValueLengthPass(chunkedValueDoc(0x40, 100, 20), 2000, true);
    }

    /*
    /**********************************************************
    /* Helper methods, verification
    /**********************************************************
     */

    // Modes for advancing to the name, all of which decode names separately
    private final static int MODE_NEXT_TOKEN = 0;
    private final static int MODE_NEXT_FIELD_NAME = 1;
    private final static int MODE_NEXT_FIELD_NAME_MATCH = 2;

    private void _verifyNameLengthFail(CBORFactory f, byte[] doc, int maxNameLen)
        throws Exception
    {
        _verifyNameLengthFail(f, doc, maxNameLen, 0);
    }

    private void _verifyNameLengthFail(CBORFactory f, byte[] doc, int maxNameLen,
            int skipEntries)
        throws Exception
    {
        for (int mode = MODE_NEXT_TOKEN; mode <= MODE_NEXT_FIELD_NAME_MATCH; ++mode) {
            for (boolean throttled : new boolean[] { false, true }) {
                final String desc = "(mode: "+mode+", throttled: "+throttled+")";
                try (JsonParser p = cborParser(f, doc, throttled)) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
                    _skipEntries(p, skipEntries);
                    _advanceToName(p, mode);
                    fail("Should not pass "+desc+", instead got name: "+p.currentName());
                } catch (StreamConstraintsException e) {
                    final String msg = e.getMessage();
                    assertTrue("Unexpected message "+desc+": "+msg,
                            msg.contains("Name length ("));
                    assertTrue("Unexpected message "+desc+": "+msg,
                            msg.contains("exceeds the maximum allowed ("+maxNameLen));
                }
            }
        }
    }

    private void _verifyNameLengthPass(CBORFactory f, byte[] doc, int expNameLen)
        throws Exception
    {
        _verifyNameLengthPass(f, doc, expNameLen, 0);
    }

    private void _verifyNameLengthPass(CBORFactory f, byte[] doc, int expNameLen,
            int skipEntries)
        throws Exception
    {
        for (int mode = MODE_NEXT_TOKEN; mode <= MODE_NEXT_FIELD_NAME_MATCH; ++mode) {
            for (boolean throttled : new boolean[] { false, true }) {
                final String desc = "(mode: "+mode+", throttled: "+throttled+")";
                try (JsonParser p = cborParser(f, doc, throttled)) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
                    _skipEntries(p, skipEntries);
                    _advanceToName(p, mode);
                    assertToken(JsonToken.FIELD_NAME, p.currentToken());
                    assertEquals(desc, expNameLen, p.currentName().length());
                    assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                    assertEquals(desc, 1, p.getIntValue());
                    assertToken(JsonToken.END_OBJECT, p.nextToken());
                    assertNull(p.nextToken());
                }
            }
        }
    }

    // Verifies that a long value (String or Binary) is read fine despite the
    // low `maxNameLength` of the factory used
    private void _verifyValueLengthPass(byte[] doc, int expValueLen, boolean binary)
        throws Exception
    {
        for (boolean throttled : new boolean[] { false, true }) {
            final String desc = "(throttled: "+throttled+")";
            try (JsonParser p = cborParser(F_CONSTRAINED, doc, throttled)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("k", p.currentName());
                if (binary) {
                    assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
                    assertEquals(desc, expValueLen, p.getBinaryValue().length);
                } else {
                    assertToken(JsonToken.VALUE_STRING, p.nextToken());
                    assertEquals(desc, expValueLen, p.getText().length());
                }
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                assertNull(p.nextToken());
            }
        }
    }

    private void _skipEntries(JsonParser p, int count) throws Exception
    {
        while (--count >= 0) {
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            p.nextToken();
            // Need to actually access the value for String to be added in
            // the stringref table
            p.getText();
        }
    }

    private void _advanceToName(JsonParser p, int mode) throws Exception
    {
        switch (mode) {
        case MODE_NEXT_TOKEN:
            p.nextToken();
            break;
        case MODE_NEXT_FIELD_NAME:
            p.nextFieldName();
            break;
        default:
            // Name will not match, but that is fine: what matters is that the
            // name-matching fast path is not taken (it never is for long names)
            p.nextFieldName(new SerializedString("zzz"));
        }
    }

    /*
    /**********************************************************
    /* Helper methods, document construction
    /**********************************************************
     */

    private byte[] textNameDoc(int nameLen, int lenMarker) {
        return textNameDoc(nameLen, lenMarker, nameLen, false);
    }

    private byte[] textNameDoc(int nameLen, int lenMarker, int contentLen) {
        return textNameDoc(nameLen, lenMarker, contentLen, false);
    }

    // Single-entry Object with text name of specified length, value of 1
    //
    // @param contentLen Number of name content bytes to actually include, which
    //    may be less than the length claimed by the encoding
    // @param indefiniteObj Whether Object itself is of indefinite length (as
    //    written by `CBORGenerator`) instead of having expected length
    private byte[] textNameDoc(int nameLen, int lenMarker, int contentLen,
            boolean indefiniteObj) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        _writeObjectStart(bytes, indefiniteObj);
        _writeHeader(bytes, 0x60, lenMarker, nameLen);
        _writeContent(bytes, contentLen);
        bytes.write(0x01); // value: 1
        _writeObjectEnd(bytes, indefiniteObj);
        return bytes.toByteArray();
    }

    private byte[] chunkedTextNameDoc(int chunkLen, int chunkCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        _writeChunked(bytes, 0x60, chunkLen, chunkCount);
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private byte[] binaryNameDoc(int nameLen) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        _writeHeader(bytes, 0x40, LEN_2_BYTE_SUFFIX, nameLen);
        _writeContent(bytes, nameLen);
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private byte[] chunkedBinaryNameDoc(int chunkLen, int chunkCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        _writeChunked(bytes, 0x40, chunkLen, chunkCount);
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    // Single-entry Object with short name and a String value of given length
    private byte[] textValueDoc(int valueLen) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x61); bytes.write('k'); // name: "k"
        _writeHeader(bytes, 0x60, LEN_2_BYTE_SUFFIX, valueLen);
        _writeContent(bytes, valueLen);
        return bytes.toByteArray();
    }

    // Single-entry Object with short name and chunked String or Binary value
    private byte[] chunkedValueDoc(int typeByte, int chunkLen, int chunkCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x61); bytes.write('k'); // name: "k"
        _writeChunked(bytes, typeByte, chunkLen, chunkCount);
        return bytes.toByteArray();
    }

    // Two-entry Object where name of the second entry is a "stringref" to the
    // (long) String value of the first one
    private byte[] stringRefNameDoc(int refdLen) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Tag 256, "stringref-namespace", needed for stringrefs to be allowed
        bytes.write(0xD9); bytes.write(0x01); bytes.write(0x00);
        bytes.write(0xA2); // Object, 2 entries
        bytes.write(0x61); bytes.write('a'); // name: "a" (too short to reference)
        // value: String long enough to become ref #0
        _writeHeader(bytes, 0x60, LEN_2_BYTE_SUFFIX, refdLen);
        _writeContent(bytes, refdLen);
        bytes.write(0xD8); bytes.write(0x19); // Tag 25, "stringref"...
        bytes.write(0x00); // ...to ref #0: name is the long String above
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private void _writeObjectStart(ByteArrayOutputStream bytes, boolean indefinite) {
        bytes.write(indefinite ? 0xBF : 0xA1);
    }

    private void _writeObjectEnd(ByteArrayOutputStream bytes, boolean indefinite) {
        if (indefinite) {
            bytes.write(0xFF);
        }
    }

    // Indefinite-length (chunked) String or Binary, as a sequence of equal-length chunks
    private void _writeChunked(ByteArrayOutputStream bytes, int typeByte,
            int chunkLen, int chunkCount) {
        bytes.write(typeByte + 31); // indefinite length...
        for (int i = 0; i < chunkCount; ++i) {
            _writeHeader(bytes, typeByte, LEN_1_BYTE_SUFFIX, chunkLen);
            _writeContent(bytes, chunkLen);
        }
        bytes.write(0xFF); // ...up to break
    }

    // @param typeByte Base value of the type byte (major type shifted in place)
    // @param lenMarker 5-bit length marker to use; `LEN_IN_TYPE_BYTE` for
    //    length encoded in the type byte itself
    private void _writeHeader(ByteArrayOutputStream bytes, int typeByte,
            int lenMarker, int len) {
        switch (lenMarker) {
        case LEN_1_BYTE_SUFFIX:
            bytes.write(typeByte + LEN_1_BYTE_SUFFIX);
            bytes.write(len);
            break;
        case LEN_2_BYTE_SUFFIX:
            bytes.write(typeByte + LEN_2_BYTE_SUFFIX);
            bytes.write(len >> 8);
            bytes.write(len);
            break;
        case LEN_4_BYTE_SUFFIX:
            bytes.write(typeByte + LEN_4_BYTE_SUFFIX);
            bytes.write(len >> 24);
            bytes.write(len >> 16);
            bytes.write(len >> 8);
            bytes.write(len);
            break;
        default: // length in type byte itself (up to 23)
            bytes.write(typeByte + len);
        }
    }

    private void _writeContent(ByteArrayOutputStream bytes, int len) {
        for (int i = 0; i < len; ++i) {
            bytes.write('a');
        }
    }

    // Name matching content written by `_writeContent()`
    private String _generateName(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append('a');
        }
        return sb.toString();
    }
}
