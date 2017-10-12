package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper;

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

    public abstract String getTypeId();
    
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
        public String getTypeId() {
            return AvroSchemaHelper.getTypeId(boolean.class);
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
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
        public String getTypeId() {
            return AvroSchemaHelper.getTypeId(double.class);
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
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
        public String getTypeId() {
            return AvroSchemaHelper.getTypeId(float.class);
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
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
        private final String _typeId;

        public IntReader(String typeId) {
            _typeId = typeId;
        }

        public IntReader() {
            this(AvroSchemaHelper.getTypeId(int.class));
        }

        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            JsonToken token = parser.decodeIntToken();
            // Character deserializer expects parser.getText() to return something. Make sure it's populated!
            if (Character.class.getName().equals(getTypeId())) {
                return parser.setString(Character.toString((char) parser.getIntValue()));
            }
            return token;
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipInt();
        }

        @Override
        public String getTypeId() {
            return _typeId;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }

        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeIntToken();
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
            return parser.decodeLongToken();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipLong();
        }
        
        @Override
        public String getTypeId() {
            return AvroSchemaHelper.getTypeId(long.class);
        }
        
        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeLongToken();
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
        public String getTypeId() {
            return null;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper);
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper) {
                super(name, skipper, null);
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
        private final String _typeId;

        public StringReader(String typeId) {
            _typeId = typeId;
        }

        public StringReader() {
            this(AvroSchemaHelper.getTypeId(String.class));
        }

        @Override
        public JsonToken decodeValue(AvroParserImpl parser) throws IOException {
            return parser.decodeStringToken();
        }

        @Override
        protected void skipValue(AvroParserImpl parser) throws IOException {
            parser.skipString();
        }

        @Override
        public String getTypeId() {
            return _typeId;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
            }

            @Override
            public JsonToken readValue(AvroReadContext parent, AvroParserImpl parser) throws IOException {
                return parser.decodeStringToken();
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
        public String getTypeId() {
            return AvroSchemaHelper.getTypeId(byte[].class);
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, getTypeId());
        }
        
        private final static class FR extends AvroFieldReader {
            public FR(String name, boolean skipper, String typeId) {
                super(name, skipper, typeId);
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
        public String getTypeId() {
            return null;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, _readers);
        }
        
        private final static class FR extends AvroFieldReader {
            public final ScalarDecoder[] _readers;

            public FR(String name, boolean skipper, ScalarDecoder[] readers) {
                super(name, skipper, null);
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
        public String getTypeId() {
            return _name;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, this, _name);
        }
        
        private final static class FR extends AvroFieldReader {
            protected final String[] _values;

            public FR(String name, boolean skipper, EnumDecoder base, String typeId) {
                super(name, skipper, typeId);
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
        private final String _typeId;
        
        public FixedDecoder(int fixedSize, String typeId) {
            _size = fixedSize;
            _typeId = typeId;
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
        public String getTypeId() {
            return _typeId;
        }

        @Override
        public AvroFieldReader asFieldReader(String name, boolean skipper) {
            return new FR(name, skipper, _size, _typeId);
        }
        
        private final static class FR extends AvroFieldReader {
            private final int _size;

            public FR(String name, boolean skipper, int size, String typeId) {
                super(name, skipper, typeId);
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
