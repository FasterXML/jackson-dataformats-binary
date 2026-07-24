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
        _verifyNameLengthFail(F_TINY, textNameDoc(200, 24), TINY_NAME_LEN);
    }

    // Name with 2-byte length suffix (marker 25)
    public void testLongNameWith2ByteLength() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(5000, 25), MAX_NAME_LEN);
    }

    // Name with 4-byte length suffix (marker 26)
    public void testLongNameWith4ByteLength() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(5000, 26), MAX_NAME_LEN);
    }

    // Name too long to fit in the input buffer: decoded in segments
    public void testLongNameLongerThanInputBuffer() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(40_000, 26), MAX_NAME_LEN);
    }

    // Limit must be checked before content is read, so that a document that
    // merely claims a huge name fails with constraints (and not EOF) exception,
    // without any attempt to buffer the content
    public void testHugeNameLengthWithoutContent() throws Exception
    {
        _verifyNameLengthFail(F_CONSTRAINED, textNameDoc(100_000_000, 26, 0),
                MAX_NAME_LEN);
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
        _verifyNameLengthPass(F_CONSTRAINED, textNameDoc(MAX_NAME_LEN, 25), MAX_NAME_LEN);
    }

    public void testChunkedNameAtMaxLength() throws Exception
    {
        _verifyNameLengthPass(F_CONSTRAINED, chunkedTextNameDoc(100, 10), MAX_NAME_LEN);
    }

    // Enforcement is approximate: shortest names, ones with length encoded in
    // the type byte itself (up to 23 bytes), are not checked at all, making 23
    // bytes the effective minimum limit
    public void testShortNameNotValidated() throws Exception
    {
        _verifyNameLengthPass(F_MINI, textNameDoc(23, 0), 23);

        // but one byte longer, and there is an explicit length to check
        _verifyNameLengthFail(F_MINI, textNameDoc(24, 24), MINI_NAME_LEN);
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
        for (int mode = MODE_NEXT_TOKEN; mode <= MODE_NEXT_FIELD_NAME_MATCH; ++mode) {
            for (boolean throttled : new boolean[] { false, true }) {
                final String desc = "(mode: "+mode+", throttled: "+throttled+")";
                try (JsonParser p = cborParser(f, doc, throttled)) {
                    assertToken(JsonToken.START_OBJECT, p.nextToken());
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
        return textNameDoc(nameLen, lenMarker, nameLen);
    }

    // Single-entry Object with text name of specified length, value of 1
    //
    // @param contentLen Number of name content bytes to actually include, which
    //    may be less than the length claimed by the encoding
    private byte[] textNameDoc(int nameLen, int lenMarker, int contentLen) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        _writeHeader(bytes, 0x60, lenMarker, nameLen);
        _writeContent(bytes, contentLen);
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private byte[] chunkedTextNameDoc(int chunkLen, int chunkCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x7F); // indefinite-length text...
        for (int i = 0; i < chunkCount; ++i) {
            _writeHeader(bytes, 0x60, 24, chunkLen);
            _writeContent(bytes, chunkLen);
        }
        bytes.write(0xFF); // ...up to break
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private byte[] binaryNameDoc(int nameLen) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        _writeHeader(bytes, 0x40, 25, nameLen);
        _writeContent(bytes, nameLen);
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    private byte[] chunkedBinaryNameDoc(int chunkLen, int chunkCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xA1); // Object, 1 entry
        bytes.write(0x5F); // indefinite-length binary...
        for (int i = 0; i < chunkCount; ++i) {
            _writeHeader(bytes, 0x40, 24, chunkLen);
            _writeContent(bytes, chunkLen);
        }
        bytes.write(0xFF); // ...up to break
        bytes.write(0x01); // value: 1
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
        _writeHeader(bytes, 0x60, 25, refdLen); // value: long String, becomes ref #0
        _writeContent(bytes, refdLen);
        bytes.write(0xD8); bytes.write(0x19); // Tag 25, "stringref"...
        bytes.write(0x00); // ...to ref #0: name is the long String above
        bytes.write(0x01); // value: 1
        return bytes.toByteArray();
    }

    // @param typeByte Base value of the type byte (major type shifted in place)
    // @param lenMarker 5-bit length marker to use; 0 for length in type byte itself
    private void _writeHeader(ByteArrayOutputStream bytes, int typeByte,
            int lenMarker, int len) {
        switch (lenMarker) {
        case 24: // 1-byte length suffix
            bytes.write(typeByte + 24);
            bytes.write(len);
            break;
        case 25: // 2-byte
            bytes.write(typeByte + 25);
            bytes.write(len >> 8);
            bytes.write(len);
            break;
        case 26: // 4-byte
            bytes.write(typeByte + 26);
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
}
