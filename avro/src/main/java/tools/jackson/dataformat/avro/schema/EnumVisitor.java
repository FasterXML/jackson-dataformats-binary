package tools.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.Set;

import tools.jackson.databind.*;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;

import org.apache.avro.Schema;

/**
 * Specific visitor for Java Enum types that are to be exposed as
 * Avro Enums. Used unless Java Enums are to be mapped to Avro Strings.
 */
public class EnumVisitor extends JsonStringFormatVisitor.Base
    implements SchemaBuilder
{
    protected final SerializationContext _provider;
    protected final JavaType _type;
    protected final DefinedSchemas _schemas;

    protected Set<String> _enums;

    public EnumVisitor(SerializationContext provider, DefinedSchemas schemas, JavaType t) {
        _schemas = schemas;
        _type = t;
        _provider = provider;
    }

    @Override
    public void enumTypes(Set<String> enums) {
        _enums = enums;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_enums == null) {
            throw new IllegalStateException("Possible enum values cannot be null");
        }

        AnnotatedClass annotations = _provider.introspectClassAnnotations(_type);
        Schema s = AvroSchemaHelper.createEnumSchema(_provider.getConfig(), _type,
                annotations, new ArrayList<>(_enums));
        _schemas.addSchema(_type, s);
        return s;
    }
}
