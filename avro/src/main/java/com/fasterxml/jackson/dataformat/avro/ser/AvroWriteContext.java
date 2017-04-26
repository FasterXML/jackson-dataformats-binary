package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    private final static Class<?> CLS_STRING = String.class;
    private final static Class<?> CLS_BIG_DECIMAL = BigDecimal.class;

    private final static Class<?> CLS_GENERIC_RECORD = GenericData.Record.class;
    private final static Class<?> CLS_GENERIC_ARRAY = GenericData.Array.class;

    protected final AvroWriteContext _parent;
    
    protected final AvroGenerator _generator;
    
    protected final Schema _schema;

    /**
     * @since 2.9
     */
    protected Object _currentValue;

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

    /*
    /**********************************************************
    /* Accessors
    /**********************************************************
     */

    @Override
    public Object getCurrentValue() {
        return _currentValue;
    }

    @Override
    public void setCurrentValue(Object v) {
        _currentValue = v;
    }
    
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
        final List<Schema> types = unionSchema.getTypes();
        // nulls should map to null type
        if (datum == null) {
            for (int i = 0, size = types.size(); i < size; ++i) {
                Schema type = types.get(i);
                if (type.getType() == Type.NULL) {
                    return i;
                }
            }
        } else {
            Class<?> raw = datum.getClass();
            if (raw == CLS_STRING) {
                return _resolveStringIndex(unionSchema, types, (String) datum);
            }
            // 26-Apr-2017, tatu: This may look odd optimization, but turns out that:
            //   (a) case of "null and ONE other type" is VERY common, and
            //   (b) cost of real lookup for POJO types is VERY expensive (due to elaborate
            //      caching Avro lib does
            int ix = _findNotNullIndex(types);
            if (ix >= 0) {
                return ix;
            }
            if (raw == CLS_BIG_DECIMAL) {
                return _resolveBigDecimalIndex(unionSchema, types, (BigDecimal) datum);
            }
            if (raw == CLS_GENERIC_RECORD) {
                return _resolveRecordIndex(unionSchema, types, (GenericData.Record) datum);
            }
            if (raw == CLS_GENERIC_ARRAY) {
                return _resolveArrayIndex(unionSchema, types, (GenericData.Array<?>) datum);
            }
            if (datum instanceof Map<?,?>) {
                return _resolveMapIndex(unionSchema, types, datum);
            }

            // !!! TODO:
            //  - ByteBuffer
            //  - Number wrappers (Integer, ...)
            
            /*
            String typeId = AvroSchemaHelper.getTypeId(datum.getClass());
            for (int i = 0, size = types.size(); i < size; ++i) {
                Schema schema = types.get(i);
                // Exact schema match?
                if (typeId.equals(AvroSchemaHelper.getTypeId(schema))) {
                    return i;
                }
            }
            */
        }
//System.err.println("Missing index for: "+datum.getClass().getName()+" ("+types.size()+") ->\n"+types);  
        return ReflectData.get().resolveUnion(unionSchema, datum);
    }

    public static Schema resolveUnionType(Schema unionSchema, Object datum) {
        final List<Schema> types = unionSchema.getTypes();
        // nulls should map to null type
        if (datum == null) {
            for (int i = 0, size = types.size(); i < size; ++i) {
                Schema type = types.get(i);
                if (type.getType() == Type.NULL) {
                    return type;
                }
            }
        } else {
            Class<?> raw = datum.getClass();
            if (raw == CLS_STRING) {
                return types.get(_resolveStringIndex(unionSchema, types, (String) datum));
            }
            // 26-Apr-2017, tatu: This may look odd optimization, but turns out that:
            //   (a) case of "null and ONE other type" is VERY common, and
            //   (b) cost of real lookup for POJO types is VERY expensive (due to elaborate
            //      caching Avro lib does
            Schema sch = _findNotNull(types);
            if (sch != null) {
                return sch;
            }
            if (raw == CLS_BIG_DECIMAL) {
                return types.get(_resolveBigDecimalIndex(unionSchema, types, (BigDecimal) datum));
            }
            if (raw == CLS_GENERIC_RECORD) {
                return types.get(_resolveRecordIndex(unionSchema, types, (GenericData.Record) datum));
            }
            if (raw == CLS_GENERIC_ARRAY) {
                return types.get(_resolveArrayIndex(unionSchema, types, (GenericData.Array<?>) datum));
            }
            if (datum instanceof Map<?,?>) {
                return types.get(_resolveMapIndex(unionSchema, types, datum));
            }

            /*
            String typeId = AvroSchemaHelper.getTypeId(datum.getClass());
            for (int i = 0, size = types.size(); i < size; ++i) {
                Schema schema = types.get(i);
                // Exact schema match?
                if (typeId.equals(AvroSchemaHelper.getTypeId(schema))) {
                    return schema;
                }
            }
            */
        }
//System.err.println("Missing schema for: "+datum.getClass().getName()+" ("+types.size()+") ->\n"+types);  
        int ix = ReflectData.get().resolveUnion(unionSchema, datum);
        return types.get(ix);
    }

    private static int _resolveStringIndex(Schema unionSchema, List<Schema> types,
            String value)
    {
        for (int i = 0, size = types.size(); i < size; ++i) {
            Schema schema = types.get(i);
            Schema.Type t = schema.getType();

            if (t == Type.STRING) {
                return i;
            }
            // Jackson serializes enums as strings, so try and find a matching schema
            if (t == Type.ENUM) { // && schema.hasEnumSymbol((String) datum)) {
                return i;
            }
            // Jackson serializes char/Character as a string, so try and find a matching schema
            if (t == Type.INT
                && value.length() == 1
                && AvroSchemaHelper.getTypeId(Character.class).equals(schema.getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))
            ) {
                return i;
            }
            // Jackson serializes char[]/Character[] as a string, so try and find a matching schema
            if (t == Type.ARRAY
                && schema.getElementType().getType() == Type.INT
                && AvroSchemaHelper.getTypeId(Character.class).equals(schema.getElementType().getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))
            ) {
                return i;
            }
        }
        return ReflectData.get().resolveUnion(unionSchema, value);
    }

    private static Schema _findNotNull(List<Schema> types)
    {
        if (types.size() == 2) {
            if (types.get(0).getType() == Type.NULL) {
                return types.get(1);
            }
            if (types.get(1).getType() == Type.NULL) {
                return types.get(0);
            }
        }
        return null;
    }

    private static int _findNotNullIndex(List<Schema> types)
    {
        if (types.size() == 2) {
            if (types.get(0).getType() == Type.NULL) {
                return 1;
            }
            if (types.get(1).getType() == Type.NULL) {
                return 0;
            }
        }
        return -1;
    }

    private static int _resolveBigDecimalIndex(Schema unionSchema, List<Schema> types,
            BigDecimal value) {
        int match = -1;

        for (int i = 0, size = types.size(); i < size; ++i) {
            Schema schema = types.get(i);
            Schema.Type t = schema.getType();

            if (t == Type.DOUBLE) {
                return i;
            }
            // BigDecimals can be shoved into a double, but optimally would be a String or byte[] with logical type information
            if (t == Type.DOUBLE) {
                match = i;
                continue;
            }
        }
        if (match < 0) {
            match = ReflectData.get().resolveUnion(unionSchema, value);
        }
        return match;
    }

    private static int _resolveMapIndex(Schema unionSchema, List<Schema> types,
            Object value)
    {
        for (int i = 0, size = types.size(); i < size; ++i) {
            if (types.get(i).getType() == Type.MAP) {
                return i;
            }
        }
        return ReflectData.get().resolveUnion(unionSchema, value);
    }

    private static int _resolveRecordIndex(Schema unionSchema, List<Schema> types,
            GenericData.Record value)
    {
        String name = value.getSchema().getFullName();
        for (int i = 0, size = types.size(); i < size; ++i) {
            Schema sch = types.get(i);
            if (sch.getType() == Type.RECORD) {
                if (name.equals(sch.getFullName())) {
                    return i;
                }
            }
        }
        return ReflectData.get().resolveUnion(unionSchema, value);
    }

    private static int _resolveArrayIndex(Schema unionSchema, List<Schema> types,
            GenericData.Array<?> value)
    {
//        String name = value.getSchema().getFullName();
        for (int i = 0, size = types.size(); i < size; ++i) {
            Schema sch = types.get(i);
            if (sch.getType() == Type.ARRAY) {
                // should we verify contents?
//                if (name.equals(sch.getFullName())) {
                    return i;
//                }
            }
        }
        return ReflectData.get().resolveUnion(unionSchema, value);
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
        return resolveUnionType(unionSchema, datum);
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
