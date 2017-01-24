package com.fasterxml.jackson.dataformat.avro;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.FormatSchema;

import com.fasterxml.jackson.dataformat.avro.deser.AvroReaderFactory;
import com.fasterxml.jackson.dataformat.avro.deser.AvroStructureReader;

/**
 * Wrapper for Schema information needed to encode and decode Avro-format
 * data.
 */
public class AvroSchema implements FormatSchema
{
    public final static String TYPE_ID = "avro";

    protected final Schema _avroSchema;

    /**
     * Lazily instantiated value reader for this schema.
     */
    protected final AtomicReference<AvroStructureReader> _reader = new AtomicReference<AvroStructureReader>();
    
    public AvroSchema(Schema asch)
    {
        _avroSchema = asch;
    }

    @Override
    public String getSchemaType() {
        return TYPE_ID;
    }

    public Schema getAvroSchema() { return _avroSchema; }

    /**
     * Method that will consider this schema instance (used as so-called "Writer Schema"),
     * and specified "Reader Schema" instance, and will either construct a new schema
     * with appropriate translations, to use for reading (if reader and writer schemas are
     * not same); or, if schemas are the same, return `this`.
     *<p>
     * Note that neither `this` instance nor `readerSchema` is ever modified: if an altered
     * version is needed, a new schema object will be constructed.
     *
     * @param readerSchema "Reader Schema" to use (in Avro terms): schema that specified how
     *    reader wants to see the data; specifies part of translation needed along with this
     *    schema (which would be "Writer Schema" in Avro terms).
     *
     * @since 2.9
     */
    public AvroSchema withReaderSchema(AvroSchema readerSchema) {
        Schema w = _avroSchema;
        Schema r = readerSchema.getAvroSchema();
        
        if (r.equals(w)) {
            return this;
        }
        Schema newSchema = Schema.applyAliases(w, r);
System.err.println("Translated schema ->\n"+newSchema.toString(true));
        return new AvroSchema(newSchema);
    }

    public AvroStructureReader getReader()
    {
        AvroStructureReader r = _reader.get();
        if (r == null) {
            AvroReaderFactory f = new AvroReaderFactory();
            r = f.createReader(_avroSchema);
            _reader.set(r);
        }
        return r;
    }

    @Override
    public String toString() {
        return String.format("{AvroSchema: name=%s}", _avroSchema.getFullName());
    }

    @Override
    public int hashCode() {
        return _avroSchema.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ((o == null) || o.getClass() != getClass()) return false;
        AvroSchema other = (AvroSchema) o;
        return _avroSchema.equals(other._avroSchema);
    }
}
