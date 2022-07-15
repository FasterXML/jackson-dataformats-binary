package tools.jackson.dataformat.avro;

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Simple {@link PolymorphicTypeValidator} implementation used by with Avro's
 * polymorphic type handling. Does actually allow all subtypes because set of
 * allowed types is dictated by Avro Schema and not by arbitrary class names.
 *<p>
 * Note that use of validator in any other context, like with formats that allow
 * arbitrary class names, would be unsafe.
 */
final class AvroSubTypeValidator
    extends PolymorphicTypeValidator.Base
{
    private static final long serialVersionUID = 3L;

    public final static AvroSubTypeValidator instance = new AvroSubTypeValidator();

    @Override
    public Validity validateBaseType(DatabindContext ctxt, JavaType baseType) {
        return Validity.ALLOWED;
    }

    @Override
    public Validity validateSubClassName(DatabindContext ctxt,
            JavaType baseType, String subClassName) {
        return Validity.ALLOWED;
    }

    @Override
    public Validity validateSubType(DatabindContext ctxt, JavaType baseType,
            JavaType subType) {
        return Validity.ALLOWED;
    }
}
