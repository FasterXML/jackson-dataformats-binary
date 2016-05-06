package com.fasterxml.jackson.dataformat.avro.ser;

import org.apache.avro.generic.GenericArray;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

public final class ArrayWriteContext
    extends AvroWriteContext
{
    protected final GenericArray<Object> _array;
    
    public ArrayWriteContext(AvroWriteContext parent, AvroGenerator generator,
            GenericArray<Object> array)
    {
        super(TYPE_ARRAY, parent, generator, array.getSchema());
        _array = array;
    }

    @Override
    public Object rawValue() { return _array; }
    
    @Override
    public final AvroWriteContext createChildArrayContext() throws JsonMappingException {
        GenericArray<Object> arr = _createArray(_schema.getElementType());
        _array.add(arr);
        return new ArrayWriteContext(this, _generator, arr);
    }
    
    @Override
    public final AvroWriteContext createChildObjectContext() throws JsonMappingException
    {
        AvroWriteContext child = _createObjectContext(_schema.getElementType());
        _array.add(child.rawValue());
        return child;
    }
    
    @Override
    public void writeValue(Object value) {
        _array.add(value);
    }

    @Override
    public void writeString(String value) {
        _array.add(value);
    }
    
    @Override
    public void appendDesc(StringBuilder sb)
    {
        sb.append('[');
        sb.append(getCurrentIndex());
        sb.append(']');
    }
}