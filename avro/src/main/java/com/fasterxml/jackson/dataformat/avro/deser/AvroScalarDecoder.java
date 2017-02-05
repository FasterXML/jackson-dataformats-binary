package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import org.apache.avro.io.Decoder;

/**
 * Helper classes for reading non-structured values, and can thereby usually
 * be accessed using simpler interface (although sometimes not: if so,
 * instances are wrapped in <code>ScalarReaderWrapper</code>s).
 */
public abstract class AvroScalarDecoder
{
    protected abstract JsonToken readValue(AvroParserImpl parser, Decoder decoder)
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
        protected JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException
        {
            int index = decoder.readIndex();
            if (index < 0 || index >= _readers.length) {
                throw new JsonParseException(parser, String.format
                        ("Invalid index (%s); union only has %d types", index, _readers.length));
            }
            return _readers[index].readValue(parser, decoder);
        }
    }
    
    protected final static class BooleanReader extends AvroScalarDecoder
    {
        @Override
        protected JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return decoder.readBoolean() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        }
    }
    
    protected final static class BytesReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            ByteBuffer bb = parser.borrowByteBuffer();
            bb = decoder.readBytes(bb);
            return parser.setBytes(bb);
        }
    }

    protected final static class DoubleReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readDouble());
        }
    }
    
    protected final static class FloatReader extends AvroScalarDecoder {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readFloat());
        }
    }
    
    protected final static class IntReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readInt());
        }
    }
    
    protected final static class LongReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException {
            return parser.setNumber(decoder.readLong());
        }
    }
    
    protected final static class NullReader extends AvroScalarDecoder
    {
        @Override public JsonToken readValue(AvroParserImpl parser, Decoder decoder) {
            return JsonToken.VALUE_NULL;
        }
    }
    
    protected final static class StringReader extends AvroScalarDecoder
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder) throws IOException
        {
            return parser.setString(decoder.readString());
        }
    }

    protected final static class EnumDecoder
        extends AvroScalarDecoder
    {
        protected final String[] _values;
        
        public EnumDecoder(List<String> enumNames)
        {
            _values = enumNames.toArray(new String[enumNames.size()]);
        }
        
        @Override
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder)
            throws IOException
        {
            int index = decoder.readEnum();
            if (index < 0 || index >= _values.length) {
                throw new IOException("Illegal Enum index ("+index+"): only "+_values.length+" entries");
            }
            return parser.setString(_values[index]);
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
        public JsonToken readValue(AvroParserImpl parser, Decoder decoder)
            throws IOException
        {
            byte[] data = new byte[_size];
            decoder.readFixed(data);
            return parser.setBytes(data);
        }
    }
}
