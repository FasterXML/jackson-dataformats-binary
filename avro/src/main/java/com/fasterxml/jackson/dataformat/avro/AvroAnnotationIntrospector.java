package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.apache.avro.reflect.AvroIgnore;

/**
 * Adds support for the following annotations from the Apache Avro implementation:
 * <ul>
 * <li>{@link AvroIgnore @AvroIgnore} - Alias for {@code @JsonIgnore(true)}</li>
 * </ul>
 */
public class AvroAnnotationIntrospector extends JacksonAnnotationIntrospector {

    @Override
    protected boolean _isIgnorable(Annotated a) {
        return a.getAnnotation(AvroIgnore.class) != null || super._isIgnorable(a);
    }
}
