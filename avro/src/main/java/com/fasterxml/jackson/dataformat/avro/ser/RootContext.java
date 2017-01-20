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

    public RootContext(AvroGenerator generator, Schema schema) {
        super(TYPE_ROOT, null, generator, schema);
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
    public void writeValue(Object value) {
        _reportError();
    }

    @Override
    public void writeString(String value) {
        _reportError();
    }

    @Override
    public void complete(BinaryEncoder encoder) throws IOException {
        if (_writer == null) {
            _writer = new NonBSGenericDatumWriter<Object>(_schema);
        }
        _writer.write(_rootValue, encoder);
    }

    @Override
    public void appendDesc(StringBuilder sb) {
        sb.append("/");
    }

    protected void _reportError() {
        throw new IllegalStateException("Can not write values directly in root context, outside of Records/Arrays");
    }
}