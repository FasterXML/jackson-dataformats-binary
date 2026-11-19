package tools.jackson.dataformat.avro.schema;

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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.dataformat.avro.annotation.AvroNamespace;

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
     *
     * @return An {@link org.apache.avro.Schema.Type#ENUM ENUM} schema.
     */
    public static Schema createEnumSchema(MapperConfig<?> config, JavaType enumType,
            AnnotatedClass annotations, List<String> values) {
        final AnnotationIntrospector intr = config.getAnnotationIntrospector();
        Class<Enum<?>> rawEnumClass = (Class<Enum<?>>) enumType.getRawClass();
        final Enum<?> defaultEnumValue = intr.findDefaultEnumValue(config, annotations,
                rawEnumClass.getEnumConstants());
        final Schema avroSchema;
        try {
            avroSchema = Schema.createEnum(
                    getTypeName(enumType),
                    config.getAnnotationIntrospector().findClassDescription(config, annotations),
                    getNamespace(enumType, annotations),
                    values,
                    defaultEnumValue != null ? defaultEnumValue.toString() : null);
        } catch (SchemaParseException spe) {
            final String msg = String.format("Problem generating Avro `Schema` for Enum type %s: %s",
                    ClassUtil.getTypeDescription(enumType), spe.getMessage());
            throw InvalidDefinitionException.from((JsonGenerator) null, msg, enumType);
        }

        return addAlias(avroSchema, annotations);
    }

    /**
     * Helper method to enclose details of expressing best Avro Schema for
     * {@link java.util.UUID}: 16-byte fixed-length binary (alternative would
     * be basic variable length "bytes").
     */
    public static Schema createUUIDSchema() {
        return Schema.createFixed("UUID", null, "java.util", 16);
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
     *<p>
     * NOTE: since 3.1.9 type id of record, enum and fixed schemas is name exactly
     * as schema has it (see {@link #getFullName}), using dot as the separator
     * for nested classes too ({@code package.Outer.Inner}): so it does not
     * necessarily equal {@link #getTypeId(Class)} of matching class
     * ({@code package.Outer$Inner}), nor can be used for loading the class as-is.
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
     * Returns the full name of a schema; this is similar to {@link Schema#getFullName()},
     * except for handling of namespaces that end with dollar sign (used for nested
     * classes by Apache Avro before 1.9), for which name is appended as-is.
     *<p>
     * NOTE: since 3.1.9 name returned for nested classes uses dot as the separator
     * ({@code package.name.ClassName.NestedClassName}), exactly as schema has it:
     * this method no longer probes for existence of classes to figure out
     * whether a namespace refers to a package or an enclosing class
     * (see [dataformats-binary#908]). Resolution of name into an actual class
     * is done by {@code AvroParser.getTypeId()}, when (and only if) type id is needed.
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
            // 17-Nov-2026, tatu: [dataformats-binary#908] No longer probe for nested
            //   classes here: no ClassLoader that could be reliably used, and should
            //   not load classes just because (possibly untrusted) schema names them
            return namespace + '.' + name;

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
}
