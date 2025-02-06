package com.fasterxml.jackson.dataformat.avro.annotation;

import java.lang.annotation.*;

/**
 * When generate logical types is enabled, annotation instructs the
 * {@link com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator AvroSchemaGenerator}
 * to declare the annotated property's logical type as "decimal" ({@link org.apache.avro.LogicalTypes.Decimal}).
 * By default, the Avro type is "bytes" ({@link org.apache.avro.Schema.Type#BYTES}), unless the field is also
 * annotated with {@link com.fasterxml.jackson.dataformat.avro.AvroFixedSize}, in which case the Avro type
 * will be "fixed" ({@link org.apache.avro.Schema.Type#FIXED}).
 * <p>
 * This annotation is only used during Avro schema generation and does not affect data serialization
 * or deserialization.
 *
 * @since 2.19
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AvroDecimal {

    /**
     * Maximum precision of decimals stored in this type.
     */
    int precision();

    /**
     * Scale must be zero or a positive integer less than or equal to the precision.
     */
    int scale();

}
