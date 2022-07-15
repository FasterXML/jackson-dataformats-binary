package tools.jackson.dataformat.avro.ser;

import tools.jackson.dataformat.avro.AvroGenerator;

/**
 * Bogus {@link AvroWriteContext} used when ignoring structured output.
 */
public class NopWriteContext extends AvroWriteContext
{
    public NopWriteContext(int type, AvroWriteContext parent, AvroGenerator generator,
            Object currValue) {
        super(type, parent, generator, null, currValue);
    }

    @Override
    public Object rawValue() { return null; }

    @Override
    public final AvroWriteContext createChildArrayContext(Object currValue) {
        return new NopWriteContext(TYPE_ARRAY, this, _generator, currValue);
    }

    @Override
    public final AvroWriteContext createChildObjectContext(Object currValue) {
        return new NopWriteContext(TYPE_OBJECT, this, _generator, currValue);
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
