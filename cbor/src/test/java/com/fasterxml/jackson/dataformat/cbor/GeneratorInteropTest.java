package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

/**
 * Unit tests geared at testing issues that were raised due to
 * inter-operability with other CBOR codec implementations
 */
public class GeneratorInteropTest extends CBORTestBase
{
    private final static byte[] TYPE_DESC_AND_TRUE = new byte[] {
        (byte) 0xD9,
        (byte) 0xD9,
        (byte) 0xF7,
        CBORConstants.BYTE_TRUE
    };

    // Test for [Issue#6], for optional writing of CBOR Type Description Tag
    public void testTypeDescriptionTag() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        // as per spec, Type Desc Tag has value 
        gen.writeTag(CBORConstants.TAG_ID_SELF_DESCRIBE);
        gen.writeBoolean(true);
        gen.close();

        _verifyBytes(out.toByteArray(), TYPE_DESC_AND_TRUE);
    }

    public void testAutoTypeDescription() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORFactory f = cborFactory();
        assertFalse(f.isEnabled(CBORGenerator.Feature.WRITE_TYPE_HEADER));
        
        CBORGenerator gen = f.createGenerator(out);
        // First, without feature, we get just a single byte doc
        gen.writeBoolean(true);
        gen.close();

        _verifyBytes(out.toByteArray(), new byte[] {
            CBORConstants.BYTE_TRUE
        });

        f.enable(CBORGenerator.Feature.WRITE_TYPE_HEADER);
        // but with auto-write
        out = new ByteArrayOutputStream();
        gen = f.createGenerator(out);
        // First, without feature, we get just a single byte doc
        gen.writeBoolean(true);
        gen.close();

        
        
        _verifyBytes(out.toByteArray(), TYPE_DESC_AND_TRUE);
    }
}
