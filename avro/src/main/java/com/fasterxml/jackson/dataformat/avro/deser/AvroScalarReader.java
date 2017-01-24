package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;

/**
 * Helper classes for reading non-structured values, and can thereby usually
 * be accessed using simpler interface (although sometimes not: if so,
 * instances are wrapped in <code>ScalarReaderWrapper</code>s).
 */
public abstract class AvroScalarReader
{
    protected abstract JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder)
        throws IOException;

    /*
    /**********************************************************************
    /* Scalar lead value decoder implementations
    /**********************************************************************
     */

    protected final static class ScalarUnionReader
        extends AvroScalarReader
    {
        public final AvroScalarReader[] _readers;

        public ScalarUnionReader(AvroScalarReader[] readers) {
            _readers = readers;
        }
        
        @Override
        protected JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException
        {
            int index = decoder.readIndex();
            if (index < 0 || index >= _readers.length) {
                throw new JsonParseException(parser, String.format
                        ("Invalid index (%s); union only has %d types", index, _readers.length));
            }
            return _readers[index].readValue(parser, decoder);
        }
    }
    
    protected final static class BooleanReader extends AvroScalarReader
    {
        @Override
        protected JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            return decoder.readBoolean() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        }
    }
    
    protected final static class BytesReader extends AvroScalarReader
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            ByteBuffer bb = parser.borrowByteBuffer();
            bb = decoder.readBytes(bb);
            return parser.setBytes(bb);
        }
    }

    protected final static class DoubleReader extends AvroScalarReader
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            return parser.setNumber(decoder.readDouble());
        }
    }
    
    protected final static class FloatReader extends AvroScalarReader {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            return parser.setNumber(decoder.readFloat());
        }
    }
    
    protected final static class IntReader extends AvroScalarReader
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            return parser.setNumber(decoder.readInt());
        }
    }
    
    protected final static class LongReader extends AvroScalarReader
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException {
            return parser.setNumber(decoder.readLong());
        }
    }
    
    protected final static class NullReader extends AvroScalarReader
    {
        @Override public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) {
            return JsonToken.VALUE_NULL;
        }
    }
    
    protected final static class StringReader extends AvroScalarReader
    {
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder) throws IOException
        {
            return parser.setString(decoder.readString());
        }
    }

    protected final static class EnumDecoder
        extends AvroScalarReader
    {
        protected final String[] _values;
        
        public EnumDecoder(Schema schema)
        {
            List<String> v = schema.getEnumSymbols();
            _values = v.toArray(new String[v.size()]);
        }
        
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder)
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
        extends AvroScalarReader
    {
        protected final int _size;
        
        public FixedDecoder(Schema schema) {
            _size = schema.getFixedSize();
        }
        
        @Override
        public JsonToken readValue(AvroParserImpl parser, BinaryDecoder decoder)
            throws IOException
        {
            byte[] data = new byte[_size];
            decoder.readFixed(data);
            return parser.setBytes(data);
        }
    }
}
