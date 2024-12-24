package tools.jackson.dataformat.avro.schema;

import java.util.Set;

import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonValueFormat;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

/**
 * Visitor for {@link java.util.UUID} type. When it is created with logicalTypesEnabled enabled,
 * Avro schema is created with logical type uuid.
 *
 * @since 2.19
 */
public class UUIDVisitor extends JsonStringFormatVisitor.Base
        implements SchemaBuilder {
    protected boolean _logicalTypesEnabled = false;


    public UUIDVisitor(boolean logicalTypesEnabled) {
        _logicalTypesEnabled = logicalTypesEnabled;
    }

    @Override
    public void format(JsonValueFormat format) {
        // Ideally, we'd recognize UUIDs, Dates etc if need be, here...
    }

    @Override
    public void enumTypes(Set<String> enums) {
        // Do nothing
    }

    @Override
    public Schema builtAvroSchema() {
        // [dataformats-binary#179]: need special help with UUIDs, to coerce into Binary
        //   (could actually be
        Schema schema = AvroSchemaHelper.createUUIDSchema();
        return this._logicalTypesEnabled ? LogicalTypes.uuid().addToSchema(schema) : schema;
    }
}
