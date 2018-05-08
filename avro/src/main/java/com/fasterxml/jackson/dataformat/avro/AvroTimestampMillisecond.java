package com.fasterxml.jackson.dataformat.avro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only used during Avro schema generation; has no effect on data (de)serialization.
 * <p>
 * Instructs the {@link com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator AvroSchemaGenerator}
 * to declare the annotated property as type "fixed" ({@link org.apache.avro.Schema.Type#FIXED Schema.Type.FIXED}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface AvroTimestampMillisecond {

}
