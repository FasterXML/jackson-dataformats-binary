package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Container for various {@link ScalarDecoder} implementations that are
 * used for providing default values for scalar-valued record properties.
 */
public class ScalarDefaults
{
    protected abstract static class DefaultsBase
        extends AvroFieldReader
    {
        protected DefaultsBase(String name) {
            super(name, false); // false -> not skip-only
        }

        @Override
        public abstract JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder)
            throws IOException;

        @Override
        public void skipValue(BinaryDecoder decoder) throws IOException {
            // nothing to skip ever
        }
    }

    protected final static class BooleanDefaults extends DefaultsBase
    {
        protected final JsonToken _defaults;

        public BooleanDefaults(String name, boolean v) {
            super(name);
            _defaults = v ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return _defaults;
        }
    }

    protected final static class StringDefaults extends DefaultsBase
    {
        protected final String _defaults;

        public StringDefaults(String name, String v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setString(_defaults);
        }
    }
    
    protected final static class BytesDefaults extends DefaultsBase
    {
        protected final byte[] _defaults;

        public BytesDefaults(String name, byte[] v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setBytes(_defaults);
        }
    }

    protected final static class DoubleDefaults extends DefaultsBase
    {
        protected final double _defaults;

        public DoubleDefaults(String name, double v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setNumber(_defaults);
        }
    }

    protected final static class FloatDefaults extends DefaultsBase
    {
        protected final float _defaults;

        public FloatDefaults(String name, float v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setNumber(_defaults);
        }
    }

    protected final static class IntDefaults extends DefaultsBase
    {
        protected final int _defaults;

        public IntDefaults(String name, int v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setNumber(_defaults);
        }
    }
    
    protected final static class LongDefaults extends DefaultsBase
    {
        protected final long _defaults;

        public LongDefaults(String name, long v) {
            super(name);
            _defaults = v;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return parser.setNumber(_defaults);
        }
    }

    protected final static class NullDefaults extends DefaultsBase
    {
        public NullDefaults(String name) {
            super(name);
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder avroDecoder) {
            return JsonToken.VALUE_NULL;
        }
    }
}
