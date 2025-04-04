package com.fasterxml.jackson.dataformat.smile;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.smile.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ParserInternalsTest extends BaseTestForSmile
{
    private final ByteQuadsCanonicalizer ROOT_SYMBOLS = ByteQuadsCanonicalizer.createRoot();

    @Test
    public void testPositiveVIntGoodCases() throws Exception
    {
        // First, couple of known good cases
        _verifyVIntOk(0, new byte[] { (byte) 0x80 });
        _verifyVIntOk(3, new byte[] { (byte) 0x83 });
        _verifyVIntOk(Integer.MAX_VALUE, new byte[] {
                0x0F, 0x7F, 0x7F, 0x7F, (byte) 0xBF
        });
    }

    @Test
    public void testPositiveVIntOverflows() throws Exception
    {
        // Bad: ends at 5th, but overflows
        _verifyVIntBad(new byte[] {
                (byte) 0x7F, 0x7F, 0x7F, 0x7F, (byte) 0xBF
        }, "Overflow in VInt", "1st byte (0x7F)"
        );
        // Bad: does not end at 5th (and overflows that way)
        _verifyVIntBad(new byte[] {
                (byte) 0x7F, 0x7F, 0x7F, 0x7F, (byte) 0x7F
        }, "Overflow in VInt", "5th byte (0x7F)"
        );
    }

    private void _verifyVIntOk(int expValue, byte[] doc) throws Exception
    {
        // First, read with no boundaries
        try (SmileParser p = _minimalParser(doc)) {
            assertEquals(expValue, p._readUnsignedVInt());
        }

        // Then incremental read
        try (SmileParser p = _minimalParser(new ThrottledInputStream(doc, 1))) {
            assertEquals(expValue, p._readUnsignedVInt());
        }
    }

    private void _verifyVIntBad(byte[] doc, String... msgs) throws Exception
    {
        // First, read with no boundaries
        try (SmileParser p = _minimalParser(doc)) {
            try {
                int val = p._readUnsignedVInt();
                fail("Should not pass, got 0x"+Integer.toHexString(val));
            } catch (StreamReadException e) {
                verifyException(e, msgs);
            }
        }

        // Then incremental read
        try (SmileParser p = _minimalParser(new ThrottledInputStream(doc, 1))) {
            try {
                int val = p._readUnsignedVInt();
                fail("Should not pass, got 0x"+Integer.toHexString(val));
            } catch (StreamReadException e) {
                verifyException(e, msgs);
            }
        }
    }

    private SmileParser _minimalParser(byte[] doc) {
        return new SmileParser(testIOContext(),
                0, 0, // flags
                null, // (codec)
                ROOT_SYMBOLS.makeChild(0), // ByteQuadsCanonicalizer
                null, // InputStream
                doc, 0, doc.length, false);
    }

    private SmileParser _minimalParser(InputStream in) {
        return new SmileParser(testIOContext(),
                0, 0, // flags
                null, // (codec)
                ROOT_SYMBOLS.makeChild(0), // ByteQuadsCanonicalizer
                in, // InputStream
                new byte[1], 0, 0, false);
    }
}
