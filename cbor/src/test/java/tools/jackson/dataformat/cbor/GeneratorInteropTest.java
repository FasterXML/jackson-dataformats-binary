package tools.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.ObjectWriteContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
    @Test
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

    @Test
    public void testAutoTypeDescription() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORFactory f = cborFactory();
        assertFalse(f.isEnabled(CBORWriteFeature.WRITE_TYPE_HEADER));

        CBORGenerator gen = (CBORGenerator) f.createGenerator(ObjectWriteContext.empty(), out);
        // First, without feature, we get just a single byte doc
        gen.writeBoolean(true);
        gen.close();

        _verifyBytes(out.toByteArray(), new byte[] {
            CBORConstants.BYTE_TRUE
        });

        f = f.rebuild()
                .enable(CBORWriteFeature.WRITE_TYPE_HEADER)
                .build();
        // but with auto-write
        out = new ByteArrayOutputStream();
        gen = (CBORGenerator) f.createGenerator(ObjectWriteContext.empty(), out);
        // First, without feature, we get just a single byte doc
        gen.writeBoolean(true);
        gen.close();

        _verifyBytes(out.toByteArray(), TYPE_DESC_AND_TRUE);
    }
}
