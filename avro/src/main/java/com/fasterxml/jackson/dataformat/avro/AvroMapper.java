package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import org.apache.avro.Schema;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
;

/**
 * Convenience {@link AvroMapper}, which is mostly similar to simply
 * constructing a mapper with {@link AvroFactory}, but also adds little
 * bit of convenience around {@link AvroSchema} generation.
 *
 * @since 2.5
 */
public class AvroMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    public AvroMapper() {
        this(new AvroFactory());
    }

    public AvroMapper(AvroFactory f) {
        super(f);
        registerModule(new AvroModule());
        setDefaultTyping(new AvroSchemaTypeResolverBuilder());
    }

    protected AvroMapper(ObjectMapper src) {
        super(src);
    }

    @Override
    public AvroMapper copy()
    {
        _checkInvalidCopy(AvroMapper.class);
        return new AvroMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public AvroFactory getFactory() {
        return (AvroFactory) _jsonFactory;
    }

    /**
     * Factory method for constructing {@link AvroSchema} by introspecting given
     * POJO type and building schema that contains specified properties.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.5
     */
    public AvroSchema schemaFor(Class<?> type) throws JsonMappingException
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Factory method for constructing {@link AvroSchema} by introspecting given
     * POJO type and building schema that contains specified properties.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.5
     */
    public AvroSchema schemaFor(JavaType type) throws JsonMappingException
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Method for reading an Avro Schema from given {@link InputStream},
     * and once done (successfully or not), closing the stream.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(InputStream in) throws IOException
    {
        try {
            return new AvroSchema(new Schema.Parser().setValidate(true)
                    .parse(in));
        } finally {
            in.close();
        }
    }

    /**
     * Convenience method for reading {@link AvroSchema} from given
     * encoded JSON representation.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(String schemaAsString) throws IOException
    {
        return new AvroSchema(new Schema.Parser().setValidate(true)
                .parse(schemaAsString));
    }

    /**
     * Convenience method for reading {@link AvroSchema} from given
     * encoded JSON representation.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(File schemaFile) throws IOException
    {
        return new AvroSchema(new Schema.Parser().setValidate(true)
                .parse(schemaFile));
    }

    public static class AvroSchemaTypeResolverBuilder extends StdTypeResolverBuilder {

        @Override
        public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
            // No default typing for Serialization - It's already encoded in the Schema object
            return null;
        }

        @Override
        public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
            if (subtypes == null) {
                BeanDescription bean = config.introspectClassAnnotations(baseType.getRawClass());
                AnnotatedClass  ac   = bean.getClassInfo();
                subtypes = config.getSubtypeResolver().collectAndResolveSubtypesByTypeId(config, ac);
            }
            return new AvroSchemaTypeDeserializer(
                baseType,
                new AvroNativeTypeIdResolver(baseType, config.getTypeFactory(), subtypes),
                getDefaultImpl() != null ? config.getTypeFactory().constructType(getDefaultImpl()) : baseType
            );
        }
    }

    public static class AvroSchemaTypeDeserializer extends TypeDeserializerBase {
        protected AvroSchemaTypeDeserializer(JavaType baseType, TypeIdResolver idRes, JavaType defaultImpl) {
            super(baseType, idRes, null, true, defaultImpl);
        }

        protected AvroSchemaTypeDeserializer(TypeDeserializerBase src, BeanProperty property) {
            super(src, property);
        }

        @Override
        public Object deserializeTypedFromObject(JsonParser jsonParser, DeserializationContext context) throws IOException {
            return deserializeTypedFromAny(jsonParser, context);
        }

        @Override
        public Object deserializeTypedFromArray(JsonParser jsonParser, DeserializationContext context) throws IOException {
            return deserializeTypedFromAny(jsonParser, context);
        }

        @Override
        public Object deserializeTypedFromScalar(JsonParser jsonParser, DeserializationContext context) throws IOException {
            return deserializeTypedFromAny(jsonParser, context);
        }

        @Override
        public Object deserializeTypedFromAny(JsonParser jsonParser, DeserializationContext context) throws IOException {
            // Primitives have no subtypes. Whatever the base type is, is correct!
            if (!_baseType.isPrimitive() && jsonParser.canReadTypeId()) {
                Object typeId = jsonParser.getTypeId();
                if (typeId != null) {
                    try {
                        return _deserializeWithNativeTypeId(jsonParser, context, typeId);
                    } catch (InvalidTypeIdException e) {
                        // If the base type is acceptable, fall through; otherwise, propogate error
                        if (_baseType.isAbstract()) {
                            throw e;
                        }
                    }
                }
            }
            JsonDeserializer<Object> deser = _findDefaultImplDeserializer(context);
            if (deser == null) {
                context.reportMappingException("Could not find deserializer for %s", _baseType);
            }
            return deser.deserialize(jsonParser, context);
        }

        @Override
        public TypeDeserializer forProperty(BeanProperty beanProperty) {
            return new AvroSchemaTypeDeserializer(this, beanProperty);
        }

        @Override
        public As getTypeInclusion() {
            return As.PROPERTY;
        }
    }
}
