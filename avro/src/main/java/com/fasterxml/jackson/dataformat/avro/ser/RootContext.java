package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.generic.*;
import org.apache.avro.io.BinaryEncoder;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

class RootContext
    extends AvroWriteContext
{
    protected final BinaryEncoder _encoder;

    /**
     * We need to keep reference to the root value here; either
     * <code>GenericContainer</code> or <code>Map</code> (yes,
     * Avro APIs are... odd).
     */
    protected Object _rootValue;

    /**
     * Lazily created instance for encoding: reused in case of root value sequences.
     */
    private NonBSGenericDatumWriter<Object> _writer;

    public RootContext(AvroGenerator generator, Schema schema, BinaryEncoder encoder) {
        super(TYPE_ROOT, null, generator, schema);
        _encoder = encoder;
    }

    @Override
    public Object rawValue() { return _rootValue; }
    
    @Override
    public final AvroWriteContext createChildArrayContext() throws JsonMappingException
    {
        // verify that root type is array (or compatible)
        switch (_schema.getType()) {
        case ARRAY:
        case UNION: // maybe
            break;
        default:
            throw new IllegalStateException("Can not write START_ARRAY; schema type is "
                    +_schema.getType());
        }
        GenericArray<Object> arr = _createArray(_schema);
        _rootValue = arr;
        return new ArrayWriteContext(this, _generator, arr);
    }
    
    @Override
    public final AvroWriteContext createChildObjectContext() throws JsonMappingException
    {
        // verify that root type is record (or compatible)
        switch (_schema.getType()) {
        case RECORD:
        case UNION: // maybe
            {
                GenericRecord rec = _createRecord(_schema);
                _rootValue = rec;
                return new ObjectWriteContext(this, _generator, rec);
            }
        case MAP: // used to not be supported
            {
                MapWriteContext ctxt = new MapWriteContext(this, _generator, _schema);
                _rootValue = ctxt.rawValue();
                return ctxt;
            }
        default:
        }
        throw new IllegalStateException("Can not write START_OBJECT; schema type is "
                +_schema.getType());
    }

    @Override
    public void writeValue(Object value) throws IOException {
        // 19-Jan-2017, tatu: Implemented to allow/support root-level scalars, esp.
        //   for Avro streams
        _writer().write(value, _encoder);
    }

    @Override
    public void writeString(String value) throws IOException {
        // 19-Jan-2017, tatu: Implemented to allow/support root-level scalars, esp.
        //   for Avro streams
        _writer().write(value, _encoder);
    }

    @Override
    public void writeNull() throws IOException {
        // 19-Jan-2017, tatu: ... is this even legal?
        _writer().write(null, _encoder);
    }

    @Override
    public void complete() throws IOException {
        // 19-Jan-2017, tatu: Gets also called for root-level scalar, in which
        //    case nothing (more) to output.
        if (_rootValue != null) {
            _writer().write(_rootValue, _encoder);
        }
        _rootValue = null;
    }

    @Override
    public void appendDesc(StringBuilder sb) {
        sb.append("/");
    }

    protected void _reportError() {
        throw new IllegalStateException("Can not write values directly in root context, outside of Records/Arrays");
    }

    private final NonBSGenericDatumWriter<Object> _writer() {
        NonBSGenericDatumWriter<Object> w = _writer;
        if (w == null){
            w = new NonBSGenericDatumWriter<Object>(_schema);
            _writer = w;
        }
        return w;
    }
}
