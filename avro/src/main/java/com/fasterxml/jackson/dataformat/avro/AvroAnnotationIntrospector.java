package com.fasterxml.jackson.dataformat.avro;

import java.util.Collections;
import java.util.List;

import org.apache.avro.reflect.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
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
 * <li>{@link Nullable @Nullable} - Alias for <code>JsonProperty(required = false)</code></li>
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

    @Override
    public List<PropertyName> findPropertyAliases(Annotated m) {
        AvroAlias ann = _findAnnotation(m, AvroAlias.class);
        if (ann == null) {
            return null;
        }
        return Collections.singletonList(PropertyName.construct(ann.alias()));
    }

    protected PropertyName _findName(Annotated a)
    {
        AvroName ann = _findAnnotation(a, AvroName.class);
        return (ann == null) ? null : PropertyName.construct(ann.value());
    }

    @Override
    public Boolean hasRequiredMarker(AnnotatedMember m) {
        if (_hasAnnotation(m, Nullable.class)) {
            return false;
        }
        return null;
    }

    @Override
    public JsonCreator.Mode findCreatorAnnotation(MapperConfig<?> config, Annotated a) {
        if (a instanceof AnnotatedConstructor) {
            AnnotatedConstructor constructor = (AnnotatedConstructor) a;
            // 09-Mar-2017, tatu: Ideally would allow mix-ins etc, but for now let's take
            //   a short-cut here:
            Class<?> declClass = constructor.getDeclaringClass();
            if (declClass.getAnnotation(Stringable.class) != null) {
                 if (constructor.getParameterCount() == 1
                         && String.class.equals(constructor.getRawParameterType(0))) {
                     return JsonCreator.Mode.DELEGATING;
                 }
            }
        }
        return null;
    }

    @Override
    public Object findSerializer(Annotated a) {
        if (a.hasAnnotation(Stringable.class)) {
            return ToStringSerializer.class;
        }
        return null;
    }
}
