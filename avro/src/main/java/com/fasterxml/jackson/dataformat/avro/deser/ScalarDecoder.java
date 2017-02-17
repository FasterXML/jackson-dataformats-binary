package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Helper classes for reading non-structured values, and can thereby usually
 * be accessed using simpler interface (although sometimes not: if so,
 * instances are wrapped in <code>ScalarDecoderWrapper</code>s).
 */
public abstract class ScalarDecoder
{
    protected abstract JsonToken decodeValue(AvroParserImpl parser)
        throws IOException;

    protected abstract void skipValue(AvroParserImpl parser)
        throws IOException;

    public abstract AvroFieldReader asFieldReader(String name, boolean skipper);
    
    /*
    /**********************************************************************
    /* Decoder implementations
    /**********************************************************************
     */

    protected final static class BooleanDecoder extends ScalarDecoder
    {
        @Override
        protected JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeBoolean();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipBoolean();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeBoolean();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipBoolean();
            }
        }
    }

    protected final static class DoubleReader extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeDouble();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipDouble();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeDouble();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipDouble();
            }
        }
    }
    
    protected final static class FloatReader extends ScalarDecoder {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeFloat();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipFloat();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeFloat();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipFloat();
            }
        }
    }
    
    protected final static class IntReader extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeInt();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipInt();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeInt();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipInt();
            }
        }
    }

    protected final static class CharReader extends ScalarDecoder {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            parser.decodeInt();
            return parser.setString(Character.toString((char)parser.getIntValue()));
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipInt();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }

        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                parser.decodeInt();
                return parser.setString(Character.toString((char)parser.getIntValue()));
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipInt();
            }
        }
    }
    
    protected final static class LongReader extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeLong();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipLong();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeLong();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipLong();
            }
        }
    }
    
    protected final static class NullReader extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) {
            return JsonToken.VALUE_NULL;
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            ; // value implied
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return JsonToken.VALUE_NULL;
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException { }
        }
    }
    
    protected final static class StringReader extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeString();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipString();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeString();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipString();
            }
        }
    }

    protected final static class BytesDecoder extends ScalarDecoder
    {
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeBytes();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipBytes();
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeBytes();
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipBytes();
            }
        }
    }
    
    protected final static class ScalarUnionDecoder extends ScalarDecoder
    {
        public final ScalarDecoder[] _readers;

        public ScalarUnionDecoder(ScalarDecoder[] readers) {
            _readers = readers;
        }

        @Override
        protected JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return _checkIndex(parser.decodeIndex()).decodeValue(parser);
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException
        {
            _checkIndex(parser.decodeIndex()).skipValue(parser);
        }

        private ScalarDecoder _checkIndex(int index) throws IOException {
            if (index < 0 || index >= _readers.length) {
                throw new IOException(String.format(
                        "Invalid Union index (%s); union only has %d types", index, _readers.length));
            }
            return _readers[index];
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, _readers);
        }
        
        private final static class FR extends AvroFieldReader {
            public final ScalarDecoder[] _readers;

            public FR(String name, boolean skipper, ScalarDecoder[] readers) {
                super(name, skipper);
                _readers = readers;
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return _checkIndex(parser.decodeIndex()).decodeValue(parser);
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                _checkIndex(parser.decodeIndex()).skipValue(parser);
            }

            private ScalarDecoder _checkIndex(int index) throws IOException {
                if (index < 0 || index >= _readers.length) {
                    throw new IOException(String.format(
                            "Invalid Union index (%s); union only has %d types", index, _readers.length));
                }
                return _readers[index];
            }
        }
    }

    protected final static class EnumDecoder extends ScalarDecoder
    {
        protected final String _name;
        protected final String[] _values;
        
        public EnumDecoder(String name, List<String> enumNames)
        {
            _name = name;
            _values = enumNames.toArray(new String[enumNames.size()]);
        }

        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.setString(_checkIndex(parser.decodeEnum()));
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            _checkIndex(parser.decodeEnum());
        }

        private final String _checkIndex(int index) throws IOException {
            if (index < 0 || index >= _values.length) {
                throw new IOException(String.format(
                        "Invalid Enum index (%s); enum '%s' only has %d types",
                        index, _name, _values.length));
            }
            return _values[index];
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, this);
        }
        
        private final static class FR extends AvroFieldReader {
            protected final String[] _values;

            public FR(String name, boolean skipper, EnumDecoder base) {
                super(name, skipper);
                _values = base._values;
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.setString(_checkIndex(parser.decodeEnum()));
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                _checkIndex(parser.decodeEnum());
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
    }

    protected final static class FixedDecoder
        extends ScalarDecoder
    {
        private final int _size;
        
        public FixedDecoder(int fixedSize) {
            _size = fixedSize;
        }
        
        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeFixed(_size);
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipFixed(_size);
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, _size);
        }
        
        private final static class FR extends AvroFieldReader {
            private final int _size;

            public FR(String name, boolean skipper, int size) {
                super(name, skipper);
                _size = size;
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeFixed(_size);
            }

            @Override
            public void skipValue(AvroParserImpl parser) throws IOException {
                parser.skipFixed(_size);
            }
        }
    }
}
