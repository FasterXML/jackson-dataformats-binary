package tools.jackson.dataformat.avro;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.ClassNameIdResolver;

/**
 * {@link tools.jackson.databind.jsontype.TypeIdResolver} for Avro type IDs embedded in schemas.
 * Avro generally uses class names, but we want to also support named subtypes so that developers
 * can easily remap the embedded type IDs to a different runtime class.
 */
public class AvroTypeIdResolver extends ClassNameIdResolver
{
    private static final long serialVersionUID = 3L;

    private final Map<String, Class<?>> _idTypes;

    public AvroTypeIdResolver(JavaType baseType,
            PolymorphicTypeValidator stv, Collection<NamedType> subTypes) {
        super(baseType, subTypes, stv);
        _idTypes = new HashMap<>();
        if (subTypes != null) {
            for (NamedType namedType : subTypes) {
                _idTypes.put(namedType.getName(), namedType.getType());
            }
        }
    }

    @Override
    protected JavaType _typeFromId(DatabindContext ctxt, String id)
    {
        // primitive types don't have subclasses
        if (_baseType.isPrimitive()) {
            return _baseType;
        }
        // check if there's a specific type we should be using for this ID
        Class<?> subType = _idTypes.get(id);
        if (subType != null) {
            id = _idFrom(ctxt, null, subType);
        }
        // [dataformats-binary#812]: Avro record names need not be Java class names
        //   (non-Java writer, changed namespace); for those return `null` and let
        //   `AvroTypeDeserializer` decide. Unlike `super._typeFromId()`, does NOT call
        //   `handleUnknownTypeId()` here, which would throw. Other failures
        //   ("Illegal subtype", PTV denial, not-a-subtype) still propagate.
        if (ctxt instanceof DeserializationContext dctxt
                && dctxt.isEnabled(DeserializationFeature.FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED)
                && !_allowedSubtypes.contains(id)) {
            throw dctxt.invalidTypeIdException(_baseType, id,
"`DeserializationFeature.FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED` is enabled and the input class is not registered using `@JsonSubTypes` annotation");
        }
        return ctxt.resolveAndValidateSubType(_baseType, id, _subTypeValidator);
    }
}
