package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.apache.avro.reflect.AvroIgnore;
import org.apache.avro.reflect.AvroName;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Adds support for the following annotations from the Apache Avro implementation:
 * <ul>
 * <li>{@link AvroIgnore @AvroIgnore} - Alias for {@link JsonIgnore @JsonIgnore(true)}</li>
 * <li>{@link AvroName @AvroName("custom Name")} - Alias for {@link JsonProperty @JsonProperty("custom name")}</li>
 * </ul>
 */
public class AvroAnnotationIntrospector extends JacksonAnnotationIntrospector {

    @Override
    protected boolean _isIgnorable(Annotated a) {
        return a.getAnnotation(AvroIgnore.class) != null || super._isIgnorable(a);
    }

    @Override
    public PropertyName findNameForSerialization(Annotated a) {
        if (a.hasAnnotation(AvroName.class)) {
            return PropertyName.construct(a.getAnnotation(AvroName.class).value());
        }
        return super.findNameForSerialization(a);
    }

    @Override
    public PropertyName findNameForDeserialization(Annotated a) {
        if (a.hasAnnotation(AvroName.class)) {
            return PropertyName.construct(a.getAnnotation(AvroName.class).value());
        }
        return super.findNameForDeserialization(a);
    }
}
