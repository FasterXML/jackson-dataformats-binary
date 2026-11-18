package com.fasterxml.jackson.dataformat.avro.schema;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.apache.avro.*;
import org.apache.avro.Schema.Parser;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.Stringable;
import org.apache.avro.specific.SpecificData;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.dataformat.avro.annotation.AvroNamespace;

public abstract class AvroSchemaHelper
{
    /**
     * Dedicated mapper for handling default values (String &lt;-&gt; JsonNode &lt;-&gt; Object)
     *
     * @since 2.11
     */
    private static final ObjectMapper DEFAULT_VALUE_MAPPER = new JsonMapper();

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
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a array element; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     *
     * @since 2.8.8
     */
    public static final String AVRO_SCHEMA_PROP_ELEMENT_CLASS = SpecificData.ELEMENT_PROP;
    /**
     * Default stringable classes
     *
     * @since 2.8.7
     */
    protected static final Set<Class<?>> STRINGABLE_CLASSES = new HashSet<>(Arrays.asList(
            URI.class, URL.class, File.class,
            BigInteger.class, BigDecimal.class,
            String.class
    ));

    /**
     * Checks if a given type is "Stringable", that is one of the default
     * {@code STRINGABLE_CLASSES}, is an {@code Enum},
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
        if (STRINGABLE_CLASSES.contains(type.getRawType()) || Enum.class.isAssignableFrom(type.getRawType())) {
            return true;
        }
        if (type.getAnnotated().getAnnotation(Stringable.class) == null) {
            return false;
        }
        for (AnnotatedConstructor constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == 1 && constructor.getRawParameterType(0) == String.class) {
                return true;
            }
        }
        return false;
    }

    protected static String getNamespace(JavaType type, AnnotatedClass annotations) {
        AvroNamespace ann = annotations.getAnnotation(AvroNamespace.class);
        return ann != null ? ann.value() : getNamespace(type.getRawClass());
    }

    protected static String getNamespace(Class<?> cls) {
        // 16-Feb-2017, tatu: Fixed as suggested by `baharclerode@github`;
        //   NOTE: was reverted in 2.8.8, but is enabled for Jackson 2.9.
        Class<?> enclosing = cls.getEnclosingClass();
        if (enclosing != null) {
            // 23-Aug-2024: Changed as per [dataformats-binary#167] 
            // Enclosing class may also be nested
            return enclosing.getName().replace('$', '.');
        }
        Package pkg = cls.getPackage();
        return (pkg == null) ? "" : pkg.getName();
    }

    protected static String getName(JavaType type) {
        return getName(type.getRawClass());
    }

    protected static String getName(Class<?> cls) {
        String name = cls.getSimpleName();
        // Alas, some characters not accepted...
        while (name.contains("[]")) {
            name = name.replace("[]", "Array");
        }
        return name;
    }

    protected static Schema unionWithNull(Schema otherSchema)
    {
        List<Schema> schemas = new ArrayList<>();
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
            if (hint != null) {
                if (hint.hasRawClass(float.class)) {
                    return Schema.create(Schema.Type.FLOAT);
                }
                if (hint.hasRawClass(long.class)) {
                    return Schema.create(Schema.Type.LONG);
                }
            }
            return Schema.create(Schema.Type.DOUBLE);
        case STRING:
            // 26-Nov-2019, tatu: [dataformats-binary#179] UUIDs are special
            if ((hint != null) && hint.hasRawClass(java.util.UUID.class)) {
                return createUUIDSchema();
            }
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

    public static Schema stringableKeyMapSchema(JavaType mapType, JavaType keyType, Schema valueSchema) {
        Schema schema = Schema.createMap(valueSchema);
        if (mapType != null && !mapType.hasRawClass(Map.class)) {
            schema.addProp(AVRO_SCHEMA_PROP_CLASS, getTypeId(mapType));
        }
        if (keyType != null && !keyType.hasRawClass(String.class)) {
            schema.addProp(AVRO_SCHEMA_PROP_KEY_CLASS, getTypeId(keyType));
        }
        return schema;
    }

    protected static <T> T throwUnsupported() {
        throw new UnsupportedOperationException("Format variation not supported");
    }

    /**
     * Initializes a record schema with metadata from the given class; this schema is returned in a non-finalized state, and still
     * needs to have fields added to it.
     */
    public static Schema initializeRecordSchema(BeanDescription bean) {
        final JavaType beanType = bean.getType();
        return addAlias(Schema.createRecord(
            getName(beanType),
            bean.findClassDescription(),
            getNamespace(beanType, bean.getClassInfo()),
            beanType.isTypeOrSubTypeOf(Throwable.class)
        ), bean);
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
     * @param intr Introspector to use to find default enum value
     * @param bean Enum type to use for name / description / namespace
     * @param values List of enum names
     *
     * @return An {@link org.apache.avro.Schema.Type#ENUM ENUM} schema.
     */
    public static Schema createEnumSchema(AnnotationIntrospector intr,
            BeanDescription bean, List<String> values)
    {
        final JavaType enumType = bean.getType();
        @SuppressWarnings("unchecked")
        Class<Enum<?>> rawEnumClass = (Class<Enum<?>>) enumType.getRawClass();
        final Enum<?> defaultEnumValue = intr.findDefaultEnumValue(bean.getClassInfo(),
                rawEnumClass.getEnumConstants());
        final String namespace = getNamespace(enumType, bean.getClassInfo());
        final Schema avroSchema;
        try {
            avroSchema = Schema.createEnum(
                getName(enumType),
                bean.findClassDescription(),
                namespace,
                values,
                defaultEnumValue != null ? defaultEnumValue.toString() : null);
        } catch (SchemaParseException spe) {
            final String msg = String.format("Problem generating Avro `Schema` for Enum type %s: %s",
                    ClassUtil.getTypeDescription(enumType), spe.getMessage());

            // 05-Jan-2025, tatu: SHOULD be able to throw like so but
            //   `SchemaBuilder` does not expose checked exceptions so need to
            // throw InvalidDefinitionException.from((JsonParser) null, msg);
            throw new IllegalArgumentException(msg, spe);
        }
        return addAlias(avroSchema, bean);
    }

    /**
     * Helper method to enclose details of expressing best Avro Schema for
     * {@link java.util.UUID}: 16-byte fixed-length binary (alternative would
     * be basic variable length "bytes").
     *
     * @since 2.11
     */
    public static Schema createUUIDSchema() {
        return Schema.createFixed("UUID", null, "java.util", 16);
    }

    /**
     * Looks for {@link AvroAlias @AvroAlias} on {@code bean} and adds it to {@code schema} if it exists
     * @param schema Schema to which the alias should be added
     * @param bean Bean to inspect for type aliases
     * @return {@code schema}, possibly with an alias added
     */
    public static Schema addAlias(Schema schema, BeanDescription bean) {
        AvroAlias ann = bean.getClassInfo().getAnnotation(AvroAlias.class);
        if (ann != null) {
            schema.addAlias(ann.alias(), ann.space().equals(AvroAlias.NULL) ? null : ann.space());
        }
        return schema;
    }

    public static String getTypeId(JavaType type) {
        return getTypeId(type.getRawClass());
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

    /**
     * Returns the type ID for this schema, or {@code null} if none is present.
     */
    public static String getTypeId(Schema schema) {
        switch (schema.getType()) {
        case RECORD:
        case ENUM:
        case FIXED:
            return getFullName(schema);
        default:
            return schema.getProp(AVRO_SCHEMA_PROP_CLASS);
        }
    }

    /**
     * Returns the full name of a schema; This is similar to {@link Schema#getFullName()}, except that it properly handles namespaces
     * that use dollar sign for nested classes (<code>package.name.ClassName$NestedClassName</code> instead of
     * <code>package.name.ClassName$.NestedClassName</code>).
     *<p>
     * NOTE: since 2.24 this method no longer probes for existence of nested classes (which required loading
     * classes named by possibly untrusted schema, using this module's {@link ClassLoader}): for namespaces that
     * use dots for nested classes (Apache Avro 1.9 and later) plain dotted name
     * (<code>package.name.ClassName.NestedClassName</code>) is returned. Resolution into actual nested class
     * is done by {@code AvroTypeIdResolver} when deserializing, and by {@link #typeIdMatches} when serializing.
     */
    public static String getFullName(Schema schema) {
        final Schema.Type type = schema.getType();
        switch (type) {
        case RECORD:
        case ENUM:
        case FIXED:
            final String namespace = schema.getNamespace();
            final String name = schema.getName();

            // Handle (presumed) common case
            if (namespace == null) {
                return name;
            }
            // 23-Aug-2024: [dataformats-binary#167] Still needed for backwards-compatibility
            // with schemas that use dollar sign for nested classes (Apache Avro before 1.9)
            final int len = namespace.length();
            if (namespace.charAt(len-1) == '$') {
                return namespace + name;
            }
            // [dataformats-binary#908]: no probing for nested classes
            return namespace + '.' + name;

        default:
            return type.getName();
        }
    }

    /**
     * Method for checking whether given Java class has the type id of given schema:
     * for named types (records, enums, fixed) this means that class name matches full name
     * of the schema, allowing for nested classes being separated by either dollar sign
     * (as in class name) or dot (as in namespaces of Apache Avro 1.9 and later).
     * Does not load any classes.
     *
     * @since 2.24
     */
    public static boolean typeIdMatches(Class<?> type, Schema schema) {
        final String typeId = getTypeId(type);
        switch (schema.getType()) {
        case RECORD:
        case ENUM:
        case FIXED:
            break;
        default:
            return typeId.equals(schema.getProp(AVRO_SCHEMA_PROP_CLASS));
        }
        final String name = schema.getName();
        final String namespace = schema.getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            return typeId.equals(name);
        }
        final int nsLen = namespace.length();
        // Namespace either ends with '$', or is separated from name by '.' ('$' in class name)
        int nameStart = nsLen;
        if (namespace.charAt(nsLen-1) != '$') {
            ++nameStart;
        }
        if ((typeId.length() != nameStart + name.length()) || !typeId.endsWith(name)) {
            return false;
        }
        if (nameStart > nsLen) {
            final char sep = typeId.charAt(nsLen);
            if (sep != '.' && sep != '$') {
                return false;
            }
        }
        for (int i = 0; i < nsLen; ++i) {
            final char c = typeId.charAt(i);
            final char nsc = namespace.charAt(i);
            // Class name may have '$' where namespace has '.' (but not vice versa)
            if ((c != nsc) && (c != '$' || nsc != '.')) {
                return false;
            }
        }
        return true;
    }

    public static JsonNode nullNode() {
        return DEFAULT_VALUE_MAPPER.nullNode();
    }

    /**
     * @since 2.11
     */
    public static JsonNode objectToJsonNode(Object defaultValue) {
        if (defaultValue == JsonProperties.NULL_VALUE) {
            return nullNode();
        }

        return DEFAULT_VALUE_MAPPER.convertValue(defaultValue, JsonNode.class);
    }

    /**
     * @since 2.11
     */
    public static Object jsonNodeToObject(JsonNode defaultJsonValue) {
        if (defaultJsonValue == null) {
            return null;
        }

        if (defaultJsonValue.isNull()) {
            return JsonProperties.NULL_VALUE;
        }

        return DEFAULT_VALUE_MAPPER.convertValue(defaultJsonValue, Object.class);
    }

    /**
     * Converts a default value represented as a string (such as from a schema specification) into a JsonNode for further handling.
     *
     * @param defaultValue The default value to parse, in the form of a JSON string
     * @return a parsed JSON representation of the default value
     * @throws JsonMappingException If {@code defaultValue} is not valid JSON
     *
     * @since 2.11
     */
    public static JsonNode parseDefaultValue(String defaultValue) throws JsonMappingException {
        if (defaultValue == null) {
            return null;
        }
        try {
            return DEFAULT_VALUE_MAPPER.readTree(defaultValue);
        } catch (JsonProcessingException e) {
            if (e instanceof JsonMappingException) {
                throw (JsonMappingException) e;
            }
            throw new JsonMappingException(null, "Failed to parse default value", e);
        }
    }
}
