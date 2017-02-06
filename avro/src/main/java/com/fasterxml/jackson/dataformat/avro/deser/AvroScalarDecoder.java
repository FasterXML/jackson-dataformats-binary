package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.core.JsonToken;

import org.apache.avro.io.Decoder;

/**
 * Helper classes for reading non-structured values, and can thereby usually
 * be accessed using simpler interface (although sometimes not: if so,
 * instances are wrapped in <code>ScalarReaderWrapper</code>s).
 */
public abstract class AvroScalarDecoder
{
    protected abstract JsonToken decodeValue(AvroParserImpl parser, Decoder decoder)
        throws IOException;

    protected abstract void skipValue(Decoder decoder)
            throws IOException;
    
    /*
    /**********************************************************************
    /* Scalar lead value decoder implementations
    /**********************************************************************
     */

    protected final static class ScalarUnionReader
        extends AvroScalarDecoder
    {
        public final AvroScalarDecoder[] _readers;

        public ScalarUnionReader(AvroScalarDecoder[] readers) {
            _readers = readers;
        }

        @Override
        protected JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException
        {
            return _checkIndex(decoder.readIndex()).decodeValue(parser, decoder);
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException
        {
            _checkIndex(decoder.readIndex()).skipValue(decoder);
        }

        private AvroScalarDecoder _checkIndex(int index) throws IOException {
            if (index < 0 || index >= _readers.length) {
                throw new IOException(String.format(
                        "Invalid Union index (%s); union only has %d types", index, _readers.length));
            }
            return _readers[index];
        }
    }

    protected final static class BooleanReader extends AvroScalarDecoder
    {
        @Override
        protected JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return decoder.readBoolean() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException
        {
            // for some reason, no `skipBoolean()` so:
            decoder.skipFixed(1);
        }
    }
    
    protected final static class BytesReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            ByteBuffer bb = parser.borrowByteBuffer();
            bb = decoder.readBytes(bb);
            return parser.setBytes(bb);
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            decoder.skipBytes();
        }
    }

    protected final static class DoubleReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readDouble());
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            // doubles have fixed length of 8 bytes
            decoder.skipFixed(8);
        }
    }
    
    protected final static class FloatReader extends AvroScalarDecoder {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readFloat());
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            // floats have fixed length of 4 bytes
            decoder.skipFixed(8);
        }
    }
    
    protected final static class IntReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readInt());
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            // ints use variable-length zigzagging; alas, no native skipping
            decoder.readInt();
        }
    }
    
    protected final static class LongReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readLong());
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            // longs use variable-length zigzagging; alas, no native skipping
            decoder.readLong();
        }
    }
    
    protected final static class NullReader extends AvroScalarDecoder
    {
        @Override public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) {
            return JsonToken.VALUE_NULL;
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            ; // value implied
        }
    }
    
    protected final static class StringReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException
        {
            return parser.setString(decoder.readString());
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            decoder.skipString();
        }
    }

    protected final static class EnumDecoder
        extends AvroScalarDecoder
    {
        protected final String _name;
        protected final String[] _values;
        
        public EnumDecoder(String name, List<String> enumNames)
        {
            _name = name;
            _values = enumNames.toArray(new String[enumNames.size()]);
        }

        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder) throws IOException
        {
            return parser.setString(_checkIndex(decoder.readEnum()));
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            _checkIndex(decoder.readEnum());
        }

        private final String _checkIndex(int index) throws IOException {
            if (index < 0 || index >= _values.length) {
                throw new IOException(String.format(
                        "Invalid Enum index (%s); enum '%s' only has %d types",
                        index, _name, _values.length));
            }
            return _values[index];
        }
    }

    protected final static class FixedDecoder
        extends AvroScalarDecoder
    {
        protected final int _size;
        
        public FixedDecoder(int fixedSize) {
            _size = fixedSize;
        }
        
        @Override
        public JsonToken decodeValue(AvroParserImpl parser, Decoder decoder)
            throws IOException
        {
            byte[] data = new byte[_size];
            decoder.readFixed(data);
            return parser.setBytes(data);
        }

        @Override
        protected void skipValue(Decoder decoder) throws IOException {
            decoder.skipFixed(_size);
        }
    }
}
