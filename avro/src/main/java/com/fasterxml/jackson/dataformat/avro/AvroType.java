package com.fasterxml.jackson.dataformat.avro;

import org.apache.avro.Schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface AvroType {
  /**
   *
   */
  Schema.Type schemaType();

  /**
   *
   */
  LogicalType logicalType() default LogicalType.NONE;

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
  int precision() default 0;

  /**
   *
   * @return
   */
  int scale() default 0;

  enum LogicalType {
    DECIMAL,
    DATE,
    TIME_MICROSECOND,
    TIMESTAMP_MICROSECOND,
    TIME_MILLISECOND,
    TIMESTAMP_MILLISECOND,
    UUID,
    NONE
  }
}
