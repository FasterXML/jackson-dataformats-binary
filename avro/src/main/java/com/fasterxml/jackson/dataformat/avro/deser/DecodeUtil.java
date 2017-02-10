package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.Decoder;

/**
 * Collection of helper methods needed for decoding.
 */
public class DecodeUtil
{
    public static boolean isEnd(Decoder dec) throws IOException {
        return (dec instanceof BinaryDecoder)
                && ((BinaryDecoder) dec).isEnd();
    }
}
