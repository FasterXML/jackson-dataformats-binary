package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

import com.fasterxml.jackson.core.JsonToken;

abstract class ArrayReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_ELEMENTS = 1;
    protected final static int STATE_END = 2;
    protected final static int STATE_DONE = 3;

    protected final AvroParserImpl _parser;

    protected int _state;
    protected long _count;

    protected String _currentName;
    
    protected ArrayReader(AvroReadContext parent, AvroParserImpl parser, Schema schema)
    {
        super(parent, TYPE_ARRAY, schema);
        _parser = parser;
    }

    public static ArrayReader construct(ScalarDecoder reader, Schema schema) {
        return new Scalar(reader, schema);
    }

    public static ArrayReader construct(AvroStructureReader reader, Schema schema) {
        return new NonScalar(reader, schema);
    }

    @Override
    public String nextFieldName() throws IOException {
        nextToken();
        return null;
    }
    
    @Override
    public String getCurrentName() {
        if (_currentName == null) {
            _currentName = _parent.getCurrentName();
        }
        return _currentName;
    }

    @Override
    protected void appendDesc(StringBuilder sb) {
        sb.append('[');
        sb.append(getCurrentIndex());
        sb.append(']');
    }

    @Override
    public Schema getSchema() {
        if (_currToken != JsonToken.START_ARRAY && _currToken != JsonToken.END_ARRAY) {
            return super.getSchema().getElementType();
        }
        return super.getSchema();
    }

    @Override
    public Object getTypeId() {
        if (_currToken != JsonToken.START_ARRAY && _currToken != JsonToken.END_ARRAY
            && super.getSchema().getProp(SpecificData.ELEMENT_PROP) != null) {
            return super.getSchema().getProp(SpecificData.ELEMENT_PROP);
        }
        return super.getTypeId();
    }

    /*
    /**********************************************************************
    /* Reader implementations for Avro arrays
    /**********************************************************************
     */

    private final static class Scalar extends ArrayReader
    {
        private final ScalarDecoder _elementReader;
        
        public Scalar(ScalarDecoder reader, Schema schema) {
            this(null, reader, null, schema);
        }

        private Scalar(AvroReadContext parent, ScalarDecoder reader, 
                AvroParserImpl parser, Schema schema) {
            super(parent, parser, schema);
            _elementReader = reader;
        }
        
        @Override
        public Scalar newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Scalar(parent, _elementReader, parser, _schema);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _index = 0;
                _count = _parser.decodeArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _parser.decodeArrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
                return null;
            }

            // all good, just need to read the element value:
            ++_index;
            JsonToken t = _elementReader.decodeValue(_parser);
            _currToken = t;
            return t;
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipArray()) > 0L) {
                while (--l >= 0) {
                    _elementReader.skipValue(parser);
                }
            }
        }
    }

    private final static class NonScalar extends ArrayReader
    {
        private final AvroStructureReader _elementReader;
        
        public NonScalar(AvroStructureReader reader, Schema schema) {
            this(null, reader, null, schema);
        }

        private NonScalar(AvroReadContext parent,
                AvroStructureReader reader, AvroParserImpl parser, Schema schema) {
            super(parent, parser, schema);
            _elementReader = reader;
        }
        
        @Override
        public NonScalar newReader(AvroReadContext parent,
                AvroParserImpl parser) {
            return new NonScalar(parent, _elementReader, parser, _schema);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _count = _parser.decodeArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _parser.decodeArrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            ++_index;
            AvroStructureReader r = _elementReader.newReader(this, _parser);
            _parser.setAvroContext(r);
            return (_currToken = r.nextToken());
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipArray()) > 0L) {
                while (--l >= 0) {
                    _elementReader.skipValue(parser);
                }
            }
        }
    }
}