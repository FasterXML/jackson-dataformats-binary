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
}
