package com.fasterxml.jackson.dataformat.avro.annotation;

import java.lang.annotation.*;

/**
 * Annotation allows to override default Avro type namespace value.
 * Default value is Java package name.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AvroNamespace {
    String value();
}
