package com.fasterxml.jackson.dataformat.avro;

import java.io.File;

import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.AvroIgnore;
import org.apache.avro.reflect.AvroName;
import org.apache.avro.reflect.Stringable;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Adds support for the following annotations from the Apache Avro implementation:
 * <ul>
 * <li>{@link AvroIgnore @AvroIgnore} - Alias for <code>JsonIgnore</code></li>
 * <li>{@link AvroName @AvroName("custom Name")} - Alias for <code>JsonProperty("custom name")</code></li>
 * <li>{@link AvroDefault @AvroDefault("default value")} - Alias for <code>JsonProperty.defaultValue</code>, to
 *     define default value for generated Schemas
 *   </li>
 * <li>{@link Stringable @Stringable} - Alias for <code>JsonCreator</code> on the constructor and <code>JsonValue</code> on
 * the {@link #toString()} method. </li>
 * </ul>
 *
 * @since 2.9
 */
public class AvroAnnotationIntrospector extends AnnotationIntrospector
{
    private static final long serialVersionUID = 1L;

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        return _findAnnotation(m, AvroIgnore.class) != null;
    }

    @Override
    public PropertyName findNameForSerialization(Annotated a) {
        return _findName(a);
    }

    @Override
    public PropertyName findNameForDeserialization(Annotated a) {
        return _findName(a);
    }

    @Override
    public String findPropertyDefaultValue(Annotated m) {
        AvroDefault ann = _findAnnotation(m, AvroDefault.class);
        return (ann == null) ? null : ann.value();
    }

    protected PropertyName _findName(Annotated a)
    {
        AvroName ann = _findAnnotation(a, AvroName.class);
        return (ann == null) ? null : PropertyName.construct(ann.value());
    }

    @Override
    public boolean hasCreatorAnnotation(Annotated a) {
        AnnotatedConstructor constructor = a instanceof AnnotatedConstructor ? (AnnotatedConstructor) a : null;
        AnnotatedClass parentClass =
            a instanceof AnnotatedConstructor && ((AnnotatedConstructor) a).getTypeContext() instanceof AnnotatedClass
            ? (AnnotatedClass) ((AnnotatedConstructor) a).getTypeContext()
            : null;
        return constructor != null && parentClass != null && parentClass.hasAnnotation(Stringable.class)
            && constructor.getParameterCount() == 1 && String.class.equals(constructor.getRawParameterType(0));
    }

    @Override
    public Object findSerializer(Annotated a) {
        if (a instanceof AnnotatedClass && a.hasAnnotation(Stringable.class) || a.getRawType() == File.class) {
            return ToStringSerializer.class;
        }
        return null;
    }
}
