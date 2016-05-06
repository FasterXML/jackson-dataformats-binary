package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.*;
import org.apache.avro.io.BinaryEncoder;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

public abstract class AvroWriteContext
    extends JsonStreamContext
{
    protected final AvroWriteContext _parent;
    
    protected final AvroGenerator _generator;
    
    protected final Schema _schema;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    protected AvroWriteContext(int type, AvroWriteContext parent,
            AvroGenerator generator, Schema schema)
    {
        super();
        _type = type;
        _parent = parent;
        _generator = generator;
        _schema = schema;
    }
    
    // // // Factory methods
    
    public static AvroWriteContext createRootContext(AvroGenerator generator, Schema schema) {
        return new RootContext(generator, schema);
    }

    /**
     * Factory method called to get a placeholder context that is only
     * in place until actual schema is handed.
     */
    public static AvroWriteContext createNullContext() {
        return NullContext.instance;
    }
    
    public abstract AvroWriteContext createChildArrayContext() throws JsonMappingException;
    public abstract AvroWriteContext createChildObjectContext() throws JsonMappingException;
    
    @Override
    public final AvroWriteContext getParent() { return _parent; }
    
    @Override
    public String getCurrentName() { return null; }

    /**
     * Method that writer is to call before it writes a field name.
     *
     * @return True if writing succeeded (for {@link ObjectWriteContext},
     *    iff column was recognized)
     */
    public boolean writeFieldName(String name) throws JsonMappingException {
        return false;
    }

    public abstract void writeValue(Object value) throws JsonMappingException;

    /**
     * @since 2.5
     */
    public abstract void writeString(String value) throws JsonMappingException;
    
    /**
     * Accessor called to link data being built with resulting object.
     */
    public abstract Object rawValue();
    
    public void complete(BinaryEncoder encoder) throws IOException {
        throw new IllegalStateException("Can not be called on "+getClass().getName());
    }
    
    public boolean canClose() { return true; }

    protected abstract void appendDesc(StringBuilder sb);
    
    // // // Overridden standard methods
    
    /**
     * Overridden to provide developer writeable "JsonPath" representation
     * of the context.
     */
    @Override
    public final String toString()
    {
        StringBuilder sb = new StringBuilder(64);
        appendDesc(sb);
        return sb.toString();
    }

    // // // Shared helper methods

    protected GenericRecord _createRecord(Schema schema) throws JsonMappingException
    {
        // Quick check: if type is Union, need to find actual record type...
        Type type = schema.getType();
        if (type == Schema.Type.UNION) {
            schema = _recordOrMapFromUnion(schema);
        }
        if (type == Schema.Type.MAP) {
            throw new IllegalStateException("_createRecord should never be called for elements of type MAP");
        }
        try {
            return new GenericData.Record(schema);
        } catch (RuntimeException e) {
            // TODO: update in 2.8
            throw new JsonMappingException("Failed to create Record type from "+type, e);
        }
    }
    
    protected GenericArray<Object> _createArray(Schema schema)
    {
        if (schema.getType() == Schema.Type.UNION) {
            Schema match = null;
            for (Schema s : schema.getTypes()) {
                if (s.getType() == Schema.Type.ARRAY) {
                    if (match != null) {
                        throw new IllegalStateException("Multiple Array types, can not figure out which to use for: "
                                +schema);
                    }
                    match = s;
                }
            }
            if (match == null) {
                throw new IllegalStateException("No Array type found in union type: "+schema);
            }
            schema = match;
        }
        return new GenericData.Array<Object>(8, schema);
    }

    protected AvroWriteContext _createObjectContext(Schema schema) throws JsonMappingException
    {
        if (schema.getType() == Schema.Type.UNION) {
            schema = _recordOrMapFromUnion(schema);
        }
        if (schema.getType() == Schema.Type.MAP) {
            return new MapWriteContext(this, _generator, schema);
        }
        return new ObjectWriteContext(this, _generator, _createRecord(schema));
    }

    protected Schema _recordOrMapFromUnion(Schema unionSchema)
    {
        Schema match = null;
        for (Schema s : unionSchema.getTypes()) {
            Schema.Type type = s.getType();
            if (type == Schema.Type.RECORD || type == Schema.Type.MAP) {
                if (match != null) {
                    throw new IllegalStateException("Multiple Record and/or Map types, can not figure out which to use for: "
                            +unionSchema);
                }
                match = s;
            }
        }
        if (match == null) {
            throw new IllegalStateException("No Record or Map type found in union type: "+unionSchema);
        }
        return match;
    }
    
    /*
    /**********************************************************
    /* Implementations
    /**********************************************************
     */

    /**
     * Virtual context implementation used when there is no real root
     * context available.
     */
    private final static class NullContext
        extends AvroWriteContext
    {
        public final static NullContext instance = new NullContext();
        
        private NullContext() {
            super(TYPE_ROOT, null, null, null);
        }

        @Override
        public Object rawValue() { return null; }
        
        @Override
        public final AvroWriteContext createChildArrayContext() {
            _reportError();
            return null;
        }
        
        @Override
        public final AvroWriteContext createChildObjectContext() {
            _reportError();
            return null;
        }
    
        @Override
        public void writeValue(Object value) {
            _reportError();
        }

        @Override
        public void writeString(String value) throws JsonMappingException {
            _reportError();
        }
        
        @Override
        public void appendDesc(StringBuilder sb) {
            sb.append("?");
        }

        protected void _reportError() {
            throw new IllegalStateException("Can not write Avro output without specifying Schema");
        }
    }
}
