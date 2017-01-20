package com.fasterxml.jackson.dataformat.avro.ser;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

/**
 * Bogus {@link AvroWriteContext} used when ignoring structured output.
 */
public class NopWriteContext extends AvroWriteContext
{
    public NopWriteContext(int type, AvroWriteContext parent, AvroGenerator generator) {
        super(type, parent, generator, null);
    }

    @Override
    public Object rawValue() { return null; }

    @Override
    public final AvroWriteContext createChildArrayContext() throws JsonMappingException {
        return new NopWriteContext(TYPE_ARRAY, this, _generator);
    }
    
    @Override
    public final AvroWriteContext createChildObjectContext() throws JsonMappingException {
        return new NopWriteContext(TYPE_OBJECT, this, _generator);
    }
    
    @Override
    public void writeValue(Object value) { }

    @Override
    public void writeString(String value) { }

    @Override
    public void writeNull() { }

    @Override
    public void appendDesc(StringBuilder sb) {
        sb.append("(...)");
    }
}
