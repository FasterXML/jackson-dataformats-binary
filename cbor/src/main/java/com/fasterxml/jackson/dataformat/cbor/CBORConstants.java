package com.fasterxml.jackson.dataformat.cbor;

/**
 * Constants used by {@link CBORGenerator} and {@link CBORParser}
 * 
 * @author Tatu Saloranta
 */
public final class CBORConstants
{
    /*
    /**********************************************************
    /* Major type constants, matching prefixes
    /**********************************************************
     */

    public final static int MAJOR_TYPE_INT_POS = 0;
    public final static int MAJOR_TYPE_INT_NEG = 1;
    public final static int MAJOR_TYPE_BYTES = 2;
    public final static int MAJOR_TYPE_TEXT = 3;
    public final static int MAJOR_TYPE_ARRAY = 4;
    public final static int MAJOR_TYPE_OBJECT = 5;
    public final static int MAJOR_TYPE_TAG = 6;
    public final static int MAJOR_TYPE_MISC = 7;

    public final static int PREFIX_TYPE_INT_POS = (MAJOR_TYPE_INT_POS << 5);
    public final static int PREFIX_TYPE_INT_NEG = (MAJOR_TYPE_INT_NEG << 5);
    public final static int PREFIX_TYPE_BYTES = (MAJOR_TYPE_BYTES << 5);
    public final static int PREFIX_TYPE_TEXT = (MAJOR_TYPE_TEXT << 5);
    public final static int PREFIX_TYPE_ARRAY = (MAJOR_TYPE_ARRAY << 5);
    public final static int PREFIX_TYPE_OBJECT = (MAJOR_TYPE_OBJECT << 5);
    public final static int PREFIX_TYPE_TAG = (MAJOR_TYPE_TAG << 5);
    public final static int PREFIX_TYPE_MISC = (MAJOR_TYPE_MISC << 5);

    /*
    /**********************************************************
    /* Other marker values
    /**********************************************************
     */
    
    public final static int SUFFIX_INDEFINITE = 0x1F;

    public final static int MASK_MAJOR_TYPE = 0xE0;

    /*
    /**********************************************************
    /* Well-known Tag Ids
    /**********************************************************
     */

    /**
     * As per spec, this is a sort of "nop" tag, useful as marker
     * for the very first root-level data item.
     */
    public final static int TAG_ID_SELF_DESCRIBE = 55799;
    
    /*
    /**********************************************************
    /* Actual type and marker bytes
    /**********************************************************
     */

    public final static byte BYTE_ARRAY_INDEFINITE = (byte) (PREFIX_TYPE_ARRAY + SUFFIX_INDEFINITE);
    public final static byte BYTE_OBJECT_INDEFINITE = (byte) (PREFIX_TYPE_OBJECT + SUFFIX_INDEFINITE);

    // 2-element array commonly used (for big float, f.ex.)
    public final static byte BYTE_ARRAY_2_ELEMENTS = (byte) (PREFIX_TYPE_ARRAY + 2);
    
    public final static byte BYTE_FALSE = (byte) (PREFIX_TYPE_MISC + 20);
    public final static byte BYTE_TRUE = (byte) (PREFIX_TYPE_MISC + 21);
    public final static byte BYTE_NULL = (byte) (PREFIX_TYPE_MISC + 22);

    public final static byte BYTE_EMPTY_STRING = (byte) (PREFIX_TYPE_TEXT);
    
    /**
     * String that is chunked
     */
    public final static byte BYTE_STRING_INDEFINITE = (byte) (PREFIX_TYPE_TEXT + SUFFIX_INDEFINITE);

    public final static byte BYTE_STRING_1BYTE_LEN = (byte) (PREFIX_TYPE_TEXT + 24);
    public final static byte BYTE_STRING_2BYTE_LEN = (byte) (PREFIX_TYPE_TEXT + 25);

    public final static byte BYTE_FLOAT16 = (byte) (PREFIX_TYPE_MISC + 25);
    public final static byte BYTE_FLOAT32 = (byte) (PREFIX_TYPE_MISC + 26);
    public final static byte BYTE_FLOAT64 = (byte) (PREFIX_TYPE_MISC + 27);
    
    public final static byte BYTE_TAG_BIGNUM_POS = (byte) (PREFIX_TYPE_TAG + 2);
    public final static byte BYTE_TAG_BIGNUM_NEG = (byte) (PREFIX_TYPE_TAG + 3);
    public final static byte BYTE_TAG_DECIMAL_FRACTION = (byte) (PREFIX_TYPE_TAG + 4);
    public final static byte BYTE_TAG_BIGFLOAT = (byte) (PREFIX_TYPE_TAG + 5);
    
    public final static byte BYTE_BREAK = (byte) 0xFF;

    public final static int INT_BREAK = 0xFF;
    
    /*
    /**********************************************************
    /* Basic UTF-8 decode/encode table
    /**********************************************************
     */

    /**
     * Additionally we can combine UTF-8 decoding info into similar
     * data table.
     * Values indicate "byte length - 1"; meaning -1 is used for
     * invalid bytes, 0 for single-byte codes, 1 for 2-byte codes
     * and 2 for 3-byte codes.
     */
    public final static int[] sUtf8UnitLengths;
    static {
        int[] table = new int[256];
        for (int c = 128; c < 256; ++c) {
            int code;

            // We'll add number of bytes needed for decoding
            if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                code = 1;
            } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                code = 2;
            } else if ((c & 0xF8) == 0xF0) {
                // 4 bytes; double-char with surrogates and all...
                code = 3;
            } else {
                // And -1 seems like a good "universal" error marker...
                code = -1;
            }
            table[c] = code;
        }
        sUtf8UnitLengths = table;
    }

    public static boolean hasMajorType(int expType, byte encoded) {
        int actual = (encoded & MASK_MAJOR_TYPE) >> 5;
        return (actual == expType);
    }
}
