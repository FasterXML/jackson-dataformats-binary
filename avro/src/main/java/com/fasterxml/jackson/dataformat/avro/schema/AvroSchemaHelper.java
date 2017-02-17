package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AvroSchemaHelper
{
    protected static String getNamespace(JavaType type) {
        Class<?> cls = type.getRawClass();
        if (cls.getEnclosingClass() != null) {
            return cls.getEnclosingClass().getName() + "$";
        }
        Package pkg = cls.getPackage();
        return (pkg == null) ? "" : pkg.getName();
    }
    
    protected static String getName(JavaType type) {
        String name = type.getRawClass().getSimpleName();
        // Alas, some characters not accepted...
        while (name.indexOf("[]") >= 0) {
            name = name.replace("[]", "Array");
        }
        return name;
    }
    
    protected static Schema unionWithNull(Schema otherSchema)
    {
        List<Schema> schemas = new ArrayList<Schema>();
        schemas.add(Schema.create(Schema.Type.NULL));

        // two cases: existing union
        if (otherSchema.getType() == Schema.Type.UNION) {
            schemas.addAll(otherSchema.getTypes());
        } else {
            // and then simpler case, no union
            schemas.add(otherSchema);
        }
        return Schema.createUnion(schemas);
    }

    public static Schema simpleSchema(JsonFormatTypes type, JavaType hint)
    {
        switch (type) {
        case BOOLEAN:
            return Schema.create(Schema.Type.BOOLEAN);
        case INTEGER:
            return Schema.create(Schema.Type.INT);
        case NULL:
            return Schema.create(Schema.Type.NULL);
        case NUMBER:
            if (hint.hasRawClass(float.class)) {
                return Schema.create(Schema.Type.FLOAT);
            }
            if (hint.hasRawClass(long.class)) {
                return Schema.create(Schema.Type.LONG);
            }
            return Schema.create(Schema.Type.DOUBLE);
        case STRING:
            return Schema.create(Schema.Type.STRING);
        case ARRAY:
        case OBJECT:
            throw new UnsupportedOperationException("Should not try to create simple Schema for: "+type);
        case ANY: // might be able to support in future
        default:
            throw new UnsupportedOperationException("Can not create Schema for: "+type+"; not (yet) supported");
        }
    }

    public static Schema numericAvroSchema(JsonParser.NumberType type)
    {
        switch (type) {
        case INT:
            return Schema.create(Schema.Type.INT);
        case BIG_INTEGER:
        case LONG:
            return Schema.create(Schema.Type.LONG);
        case FLOAT:
            return Schema.create(Schema.Type.FLOAT);
        case BIG_DECIMAL:
        case DOUBLE:
            return Schema.create(Schema.Type.DOUBLE);
        default:
            throw new IllegalStateException("Unrecognized number type: "+type);
        }
    }

    public static Schema anyNumberSchema()
    {
        return Schema.createUnion(Arrays.asList(
                Schema.create(Schema.Type.INT),
                Schema.create(Schema.Type.LONG),
                Schema.create(Schema.Type.DOUBLE)
                ));
    }
    
    protected static <T> T throwUnsupported() {
        throw new UnsupportedOperationException("Format variation not supported");
    }
}
