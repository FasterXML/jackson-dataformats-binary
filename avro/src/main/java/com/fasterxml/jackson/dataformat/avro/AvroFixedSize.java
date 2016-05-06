package com.fasterxml.jackson.dataformat.avro;

import java.lang.annotation.*;

/**
 * Only used during Avro schema generation; has no effect on data (de)serialization.
 * <p>
 * Instructs the {@link com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator AvroSchemaGenerator}
 * to declare the annotated property as type "fixed" ({@link org.apache.avro.Schema.Type#FIXED Schema.Type.FIXED}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface AvroFixedSize {
    /**
     * The name of the type in the generated schema
     */
    String typeName();

    /**
     * The namespace of the type in the generated schema (optional)
     */
    String typeNamespace() default "";

    /**
     * The fixed size, in bytes, of the value contained in this field
     */
    int size();
}
