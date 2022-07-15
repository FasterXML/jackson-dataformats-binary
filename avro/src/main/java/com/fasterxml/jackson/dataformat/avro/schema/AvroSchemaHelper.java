package com.fasterxml.jackson.dataformat.avro.schema;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.Stringable;
import org.apache.avro.specific.SpecificData;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.databind.util.SimpleLookupCache;

import com.fasterxml.jackson.dataformat.avro.annotation.AvroNamespace;

public abstract class AvroSchemaHelper
{
    /**
     * Dedicated mapper for handling default values (String &lt;-&gt; JsonNode &lt;-&gt; Object)
     */
    private static final ObjectMapper DEFAULT_VALUE_MAPPER = new JsonMapper();

    /**
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a value; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     */
    public static final String AVRO_SCHEMA_PROP_CLASS = SpecificData.CLASS_PROP;

    /**
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a map key; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     */
    public static final String AVRO_SCHEMA_PROP_KEY_CLASS = SpecificData.KEY_CLASS_PROP;

    /**
     * Constant used by native Avro Schemas for indicating more specific
     * physical class of a array element; referenced indirectly to reduce direct
     * dependencies to the standard avro library.
     */
    public static final String AVRO_SCHEMA_PROP_ELEMENT_CLASS = SpecificData.ELEMENT_PROP;

    /**
     * Default stringable classes
     */
    protected static final Set<Class<?>> STRINGABLE_CLASSES = new HashSet<Class<?>>(Arrays.asList(
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
            return enclosing.getName() + "$";
        }
        Package pkg = cls.getPackage();
        return (pkg == null) ? "" : pkg.getName();
    }

    protected static String getTypeName(JavaType type) {
        String name = type.getRawClass().getSimpleName();
        // Alas, some characters not accepted...
        if (name.endsWith("[]")) {
            name = name.replaceAll("[]", "Array");
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
    public static Schema initializeRecordSchema(MapperConfig<?> config, JavaType type,
            AnnotatedClass annotations) {
        return addAlias(Schema.createRecord(
                getTypeName(type),
                config.getAnnotationIntrospector().findClassDescription(config, annotations),
            getNamespace(type, annotations),
            type.isTypeOrSubTypeOf(Throwable.class)
        ), annotations);
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
     * @param values List of enum names
     * @return An {@link org.apache.avro.Schema.Type#ENUM ENUM} schema.
     */
    public static Schema createEnumSchema(MapperConfig<?> config, JavaType enumType,
            AnnotatedClass annotations, List<String> values) {
        return addAlias(Schema.createEnum(
                getTypeName(enumType),
                config.getAnnotationIntrospector().findClassDescription(config, annotations),
                getNamespace(enumType, annotations), values
        ), annotations);
    }

    /**
     * Helper method to enclose details of expressing best Avro Schema for
     * {@link java.util.UUID}: 16-byte fixed-length binary (alternative would
     * be basic variable length "bytes").
     *
     * @since 2.11
     */
    public static Schema createUUIDSchema() {
        return Schema.createFixed("UUID", "", "java.util", 16);
    }

    /**
     * Looks for {@link AvroAlias @AvroAlias} on {@code bean} and adds it to {@code schema} if it exists
     * @param schema Schema to which the alias should be added
     * @param annotations Set of possibly relevant annotations
     * @return {@code schema}, possibly with an alias added
     */
    public static Schema addAlias(Schema schema, AnnotatedClass annotations) {
        AvroAlias ann = annotations.getAnnotation(AvroAlias.class);
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
     * Returns the full name of a schema; This is similar to {@link Schema#getFullName()}, except that it properly handles namespaces for
     * nested classes. (<code>package.name.ClassName$NestedClassName</code> instead of <code>package.name.ClassName$.NestedClassName</code>)
     * <p>
     *     <b>WARNING!</b> This method has to probe for nested classes in order to resolve whether or not a schema references a top-level
     *     class or a nested class and return the corresponding name for each.
     * </p>
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
            final int len = namespace.length();
            if (namespace.charAt(len-1) == '$') {
                return namespace + name;
            }
            // 19-Sep-2020, tatu: Due to very expensive contortions of lookups introduced
            //   in [dataformats-binary#195], attempts to resolve [dataformats-binary#219]
            //   isolated into separate class
            return FullNameResolver.instance.resolve(namespace, name);

        default:
            return type.getName();
        }
    }

    public static JsonNode nullNode() {
        return DEFAULT_VALUE_MAPPER.nullNode();
    }

    public static JsonNode objectToJsonNode(Object defaultValue) {
        if (defaultValue == JsonProperties.NULL_VALUE) {
            return nullNode();
        }
        return DEFAULT_VALUE_MAPPER.convertValue(defaultValue, JsonNode.class);
    }

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
     * @throws DatabindException If {@code defaultValue} is not valid JSON
     */
    public static JsonNode parseDefaultValue(String defaultValue) throws DatabindException {
        if (defaultValue == null) {
            return null;
        }
        try {
            return DEFAULT_VALUE_MAPPER.readTree(defaultValue);
        } catch (JacksonException e) {
            if (e instanceof DatabindException) {
                throw (DatabindException) e;
            }
            throw DatabindException.from((JsonParser) null, "Failed to parse default value", e);
        }
    }

    private final static class FullNameResolver {
        private final SimpleLookupCache<FullNameKey, String> SCHEMA_NAME_CACHE = new SimpleLookupCache<>(80, 800);

        public final static FullNameResolver instance = new FullNameResolver();

        public String resolve(final String namespace, final String name) {
            final FullNameKey cacheKey = new FullNameKey(namespace, name);
            String schemaName = SCHEMA_NAME_CACHE.get(cacheKey);

            if (schemaName == null) {
                schemaName = _resolve(cacheKey);
                SCHEMA_NAME_CACHE.put(cacheKey, schemaName);
            }
            return schemaName;
        }

        private static String _resolve(FullNameKey key) {
            // 28-Feb-2020: [dataformats-binary#195] somewhat complicated logic of trying
            //     to support differences between avro-lib 1.8 and 1.9...
            //     Check if this is a nested class
            // 19-Sep-2020, tatu: This is a horrible, horribly inefficient and all-around
            //    wrong mechanism. To be abolished if possible.
            final String nestedClassName = key.nameWithSeparator('$');
            try {
                Class.forName(nestedClassName);
                return nestedClassName;
            } catch (ClassNotFoundException e) {
                // Could not find a nested class, must be a regular class
                return key.nameWithSeparator('.');
            }
        }
    }

    private final static class FullNameKey {
        private final String _namespace, _name;
        private final int _hashCode;

        public FullNameKey(String namespace, String name) {
            _namespace = namespace;
            _name = name;
            _hashCode = namespace.hashCode() + name.hashCode();
        }

        public String nameWithSeparator(char sep) {
            final StringBuilder sb = new StringBuilder(1 + _namespace.length() + _name.length());
            return sb.append(_namespace).append(sep).append(_name).toString();
        }

        @Override
        public int hashCode() { return _hashCode; }

        @Override
        public boolean equals(Object o) { // lgtm [java/unchecked-cast-in-equals]
            if (o == this) return true;
            if (o == null) return false;
            // Only used internally don't bother with type checks
            final FullNameKey other = (FullNameKey) o;
            return other._name.equals(_name) && other._namespace.equals(_namespace);
        }
    }
}
