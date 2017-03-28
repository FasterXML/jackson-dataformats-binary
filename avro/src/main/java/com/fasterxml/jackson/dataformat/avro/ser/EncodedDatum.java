package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;

import org.apache.avro.io.Encoder;

import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

/**
 * Interface for handling opaque avro-encoded objects. These can be written with {@link AvroGenerator#writeEmbeddedObject(Object)} and will
 * be written directly to the encoder.
 */
public interface EncodedDatum {

    /**
     * Callback invoked when it is time to write this datum to the output
     *
     * @param encoder Encoder to which the datum should be written
     * @throws IOException if there was an error writing out the datum
     */
    void write(Encoder encoder) throws IOException;
}
