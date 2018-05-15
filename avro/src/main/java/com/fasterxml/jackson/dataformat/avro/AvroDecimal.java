package com.fasterxml.jackson.dataformat.avro;

import org.apache.avro.Schema;

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
public @interface AvroDecimal {
    /**
     *
     */
    Schema.Type schemaType() default Schema.Type.BYTES;

    /**
     * The name of the type in the generated schema
     */
    String typeName() default "";

    /**
     * The namespace of the type in the generated schema (optional)
     */
    String typeNamespace() default "";

    /**
     * The size when the schemaType is FIXED.
     */
    int fixedSize() default 0;
    /**
     * The maximum precision of decimals stored in this type.
     */
    int precision();

    /**
     *
     * @return
     */
    int scale() default 0;
}
