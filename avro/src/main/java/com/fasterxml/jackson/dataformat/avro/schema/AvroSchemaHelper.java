package com.fasterxml.jackson.dataformat.avro.schema;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.reflect.Stringable;
import org.apache.avro.specific.SpecificData;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.util.ClassUtil;

public abstract class AvroSchemaHelper
{
    /**
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a value; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     *
     * @since 2.8.7
     */
    public static final String AVRO_SCHEMA_PROP_CLASS = SpecificData.CLASS_PROP;

    /**
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a map key; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     *
     * @since 2.8.7
     */
    public static final String AVRO_SCHEMA_PROP_KEY_CLASS = SpecificData.KEY_CLASS_PROP;

    /**
     * Default stringable classes
     *
     * @since 2.8.7
     */
    protected static final Set<Class<?>> STRINGABLE_CLASSES = new HashSet<Class<?>>(Arrays.asList(
            URI.class, URL.class, File.class,
            BigInteger.class, BigDecimal.class,
            String.class
    ));

    /**
     * Checks if a given type is "Stringable", that is one of the default {@link #STRINGABLE_CLASSES},
     * or is annotated with
     * {@link Stringable @Stringable} and has a constructor that takes a single string argument capable of deserializing the output of its
     * {@code toString()} method.
     *
     * @param type
     *     Type to check if it can be serialized to a Avro string schema
     *
     * @return {@code true} if it can be stored in a string schema, otherwise {@code false}
     */
    public static boolean isStringable(AnnotatedClass type) {
        if (STRINGABLE_CLASSES.contains(type.getRawType())) {
            return true;
        }
        if (!type.hasAnnotation(Stringable.class)) {
            return false;
        }
        for (AnnotatedConstructor constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == 1 && constructor.getRawParameterType(0) == String.class) {
                return true;
            }
        }
        return false;
    }

    protected static String getNamespace(JavaType type) {
        Class<?> cls = type.getRawClass();
        // 16-Feb-2017, tatu: Fixed as suggested by `baharclerode@github`;
        //   NOTE: was revert in 2.8.8, but is enabled for Jackson 2.9.
        Class<?> enclosing = cls.getEnclosingClass();
        if (enclosing != null) {
            return enclosing.getName() + "$";
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
            // 16-Feb-2017, tatu: Fixed as suggested by `baharclerode@github`
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

    public static Schema numericAvroSchema(JsonParser.NumberType type) {
        switch (type) {
        case INT:
            return Schema.create(Schema.Type.INT);
        case LONG:
            return Schema.create(Schema.Type.LONG);
        case FLOAT:
            return Schema.create(Schema.Type.FLOAT);
        case DOUBLE:
            return Schema.create(Schema.Type.DOUBLE);
        case BIG_INTEGER:
        case BIG_DECIMAL:
            return Schema.create(Schema.Type.STRING);
        default:
        }
        throw new IllegalStateException("Unrecognized number type: "+type);
    }

    public static Schema numericAvroSchema(JsonParser.NumberType type, JavaType hint) {
        Schema schema = numericAvroSchema(type);
        if (hint != null) {
            schema.addProp(AVRO_SCHEMA_PROP_CLASS, getTypeId(hint));
        }
        return schema;
    }

    /**
     * Helper method for constructing type-tagged "native" Avro Schema instance.
     *
     * @since 2.8.7
     */
    public static Schema typedSchema(Schema.Type nativeType, JavaType javaType) {
        Schema schema = Schema.create(nativeType);
        schema.addProp(AVRO_SCHEMA_PROP_CLASS, getTypeId(javaType));
        return schema;
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

    public static String getTypeId(JavaType type) {
        return getTypeId(type.getRawClass());
    }

    /**
     * Initializes a record schema with metadata from the given class; this schema is returned in a non-finalized state, and still
     * needs to have fields added to it.
     */
    public static Schema initializeRecordSchema(BeanDescription bean) {
        return Schema.createRecord(
            getName(bean.getType()),
            bean.findClassDescription(),
            getNamespace(bean.getType()),
            bean.getType().isTypeOrSubTypeOf(Throwable.class)
        );
    }

    /**
     * Parses a JSON-formatted representation of a schema
     */
    public static Schema parseJsonSchema(String json) {
        Schema.Parser parser = new Parser();
        return parser.parse(json);
    }

    /**
     * Constructs a new enum schema
     *
     * @param bean Enum type to use for name / description / namespace
     * @param values List of enum names
     * @return An {@link org.apache.avro.Schema.Type#ENUM ENUM} schema.
     */
    public static Schema createEnumSchema(BeanDescription bean, List<String> values) {
        return Schema.createEnum(getName(bean.getType()), bean.findClassDescription(), getNamespace(bean.getType()), values);
    }

    /**
     * Returns the Avro type ID for a given type
     */
    public static String getTypeId(Class<?> type) {
        // Primitives use the name of the wrapper class as their type ID
        if (type.isPrimitive()) {
            return ClassUtil.wrapperType(type).getName();
        }
        return type.getName();
    }
}
