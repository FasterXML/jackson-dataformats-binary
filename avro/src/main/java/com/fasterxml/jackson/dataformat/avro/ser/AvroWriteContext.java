package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.UnresolvedUnionException;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.reflect.ReflectData;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroGenerator;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper;

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
    
    public static AvroWriteContext createRootContext(AvroGenerator generator, Schema schema,
            BinaryEncoder encoder) {
        return new RootContext(generator, schema, encoder);
    }

    /**
     * Factory method called to get a placeholder context that is only
     * in place until actual schema is handed.
     */
    public static AvroWriteContext nullContext() {
        return NullContext.instance;
    }

    public abstract AvroWriteContext createChildArrayContext() throws JsonMappingException;
    public abstract AvroWriteContext createChildObjectContext() throws JsonMappingException;

    public void complete() throws IOException {
        throw new IllegalStateException("Can not be called on "+getClass().getName());
    }


    public AvroWriteContext createChildObjectContext(Object object) throws JsonMappingException { return createChildObjectContext(); }

    /* Accessors
    /**********************************************************
     */

    @Override
    public final AvroWriteContext getParent() { return _parent; }

    @Override
    public String getCurrentName() { return null; }

    /*
    /**********************************************************
    /* Write methods
    /**********************************************************
     */
    
    /**
     * Method that writer is to call before it writes a field name.
     *
     * @return True if writing succeeded (for {@link ObjectWriteContext},
     *    iff column was recognized)
     */
    public boolean writeFieldName(String name) throws IOException {
        return false;
    }

    public abstract void writeValue(Object value) throws IOException;

    /**
     * @since 2.5
     */
    public abstract void writeString(String value) throws IOException;

    /**
     * @since 2.8
     */
    public abstract void writeNull() throws IOException;

    /**
     * Accessor called to link data being built with resulting object.
     */
    public abstract Object rawValue();

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
            // alas, generator not passed to us
            throw new JsonMappingException(null, "Failed to create Record type from "+type, e);
        }
    }

    protected GenericArray<Object> _createArray(Schema schema)
    {
        if (schema.getType() == Schema.Type.UNION) {
            int arraySchemaIndex = schema.getIndexNamed(Type.ARRAY.getName());
            if (arraySchemaIndex < 0) {
                throw new IllegalStateException("No Array type found in union type: "+schema);
            }
            schema = schema.getTypes().get(arraySchemaIndex);
        }
        return new GenericData.Array<Object>(8, schema);
    }

    protected AvroWriteContext _createObjectContext(Schema schema) throws JsonMappingException {
        if (schema.getType() == Type.UNION) {
            schema = _recordOrMapFromUnion(schema);
        }
        return _createObjectContext(schema, null); // Object doesn't matter as long as schema isn't a union
    }

    protected AvroWriteContext _createObjectContext(Schema schema, Object object) throws JsonMappingException
    {
        Type type = schema.getType();
        if (type == Schema.Type.UNION) {
            try {
                schema = resolveUnionSchema(schema, object);
            } catch (UnresolvedUnionException e) {
                // couldn't find an exact match
                schema = _recordOrMapFromUnion(schema);
            }
            type = schema.getType();
        }
        if (type == Schema.Type.MAP) {
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

    /**
     * Resolves the sub-schema from a union that should correspond to the {@code datum}.
     *
     * @param unionSchema Union of schemas from which to choose
     * @param datum       Object that needs to map to one of the schemas in {@code unionSchema}
     * @return Index into {@link Schema#getTypes() unionSchema.getTypes()} that matches {@code datum}
     * @see #resolveUnionSchema(Schema, Object)
     * @throws org.apache.avro.UnresolvedUnionException if {@code unionSchema} does not have a schema that can encode {@code datum}
     */
    public static int resolveUnionIndex(Schema unionSchema, Object datum) {
        if (datum != null) {
            int subOptimal = -1;
            for(int i = 0, size = unionSchema.getTypes().size(); i < size; i++) {
                Schema schema = unionSchema.getTypes().get(i);
                // Exact schema match?
                if (AvroSchemaHelper.getTypeId(datum.getClass()).equals(AvroSchemaHelper.getTypeId(schema))) {
                    return i;
                }
                if (datum instanceof BigDecimal) {
                    // BigDecimals can be shoved into a double, but optimally would be a String or byte[] with logical type information
                    if (schema.getType() == Type.DOUBLE) {
                        subOptimal = i;
                        continue;
                    }
                }
                if (datum instanceof String) {
                    // Jackson serializes enums as strings, so try and find a matching schema
                    if (schema.getType() == Type.ENUM && schema.hasEnumSymbol((String) datum)) {
                        return i;
                    }
                    // Jackson serializes char/Character as a string, so try and find a matching schema
                    if (schema.getType() == Type.INT
                        && ((String) datum).length() == 1
                        && AvroSchemaHelper.getTypeId(Character.class).equals(schema.getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))
                    ) {
                        return i;
                    }
                    // Jackson serializes char[]/Character[] as a string, so try and find a matching schema
                    if (schema.getType() == Type.ARRAY
                        && schema.getElementType().getType() == Type.INT
                        && AvroSchemaHelper.getTypeId(Character.class).equals(schema.getElementType().getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))
                    ) {
                        return i;
                    }
                }
            }
            // Did we find a sub-optimal match?
            if (subOptimal != -1) {
                return subOptimal;
            }
        }
        return ReflectData.get().resolveUnion(unionSchema, datum);
    }

    /**
     * Resolves the sub-schema from a union that should correspond to the {@code datum}.
     *
     * @param unionSchema Union of schemas from which to choose
     * @param datum       Object that needs to map to one of the schemas in {@code unionSchema}
     * @return Schema that matches {@code datum}
     * @see #resolveUnionIndex(Schema, Object)
     * @throws org.apache.avro.UnresolvedUnionException if {@code unionSchema} does not have a schema that can encode {@code datum}
     */
    public static Schema resolveUnionSchema(Schema unionSchema, Object datum) {
        return unionSchema.getTypes().get(resolveUnionIndex(unionSchema, datum));
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
        public void writeString(String value) {
            _reportError();
        }

        @Override
        public void writeNull() {
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
