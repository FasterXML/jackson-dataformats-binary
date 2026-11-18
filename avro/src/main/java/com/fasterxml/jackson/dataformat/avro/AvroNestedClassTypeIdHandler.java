package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;

/**
 * Handler registered by {@link AvroModule} to let standard (non-Avro) type id
 * resolvers -- like one used for {@code @JsonTypeInfo(use = Id.CLASS)} --
 * resolve native type ids of nested classes: Avro schemas use dot as the
 * separator between enclosing and nested class ({@code package.Outer.Inner})
 * so such ids are not class names as-is.
 * {@link AvroTypeIdResolver} handles this by itself.
 *
 * @since 2.24
 */
class AvroNestedClassTypeIdHandler
    extends DeserializationProblemHandler
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    @Override
    public JavaType handleUnknownTypeId(DeserializationContext ctxt,
            JavaType baseType, String subTypeId, TypeIdResolver idResolver,
            String failureMsg) throws IOException
    {
        // Only for native type ids from Avro content; and not if probed already
        if ((idResolver instanceof AvroTypeIdResolver)
                || !(ctxt.getParser() instanceof AvroParser)) {
            return null;
        }
        final PolymorphicTypeValidator ptv = ctxt.getConfig().getPolymorphicTypeValidator();
        final String resolved = AvroTypeIdResolver._findNestedTypeId(ctxt, baseType, subTypeId, ptv);
        if ((resolved == null) || resolved.equals(subTypeId)) {
            return null;
        }
        return ctxt.resolveAndValidateSubType(baseType, resolved, ptv);
    }
}
