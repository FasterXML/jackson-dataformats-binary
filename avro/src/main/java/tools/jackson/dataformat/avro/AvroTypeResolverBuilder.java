package tools.jackson.dataformat.avro;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * @since 2.9
 */
public class AvroTypeResolverBuilder extends StdTypeResolverBuilder
{
    protected AvroTypeResolverBuilder(JsonTypeInfo.Value config) {
        super(config);
    }

    public static AvroTypeResolverBuilder construct(JsonTypeInfo.Value config) {
        if (config == null) {
            config = JsonTypeInfo.Value.construct(JsonTypeInfo.Id.CUSTOM, // could be NONE, but there is type discriminator in Avro...
                    JsonTypeInfo.As.PROPERTY, // N/A for custom
                    "@class", // similarly, N/A
                    null, // defaultImpl
                    false, // id visible
                    null); // require type info for subtypes
        }
        // no use for annotation info, at this point?
        return new AvroTypeResolverBuilder(config);
    }

    @Override
    public TypeSerializer buildTypeSerializer(SerializationContext ctxt, JavaType baseType,
            Collection<NamedType> subtypes) {
        // All type information is encoded in the schema, never in the data.
        return null;
    }

    @Override
    public TypeDeserializer buildTypeDeserializer(DeserializationContext ctxt, JavaType baseType,
            Collection<NamedType> subtypes)
    {
        Class<?> rawDefault = getDefaultImpl();
        JavaType defaultImpl = (rawDefault == null) ? null :
            ctxt.constructType(rawDefault);
        TypeIdResolver idRes = idResolver(ctxt, baseType, subTypeValidator(ctxt),
                subtypes, true, false);
        return new AvroTypeDeserializer(baseType,
                idRes, getTypeProperty(), isTypeIdVisible(), defaultImpl);
    }

    @Override
    protected TypeIdResolver idResolver(DatabindContext ctxt,
            JavaType baseType, PolymorphicTypeValidator subtypeValidator,
            Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
        return new AvroTypeIdResolver(baseType, subtypeValidator,
                subtypes);
    }
}
