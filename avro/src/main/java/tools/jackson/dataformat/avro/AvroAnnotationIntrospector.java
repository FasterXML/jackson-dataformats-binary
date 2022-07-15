package tools.jackson.dataformat.avro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.avro.reflect.*;

import com.fasterxml.jackson.annotation.JsonCreator;

import tools.jackson.core.Version;

import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.dataformat.avro.apacheimpl.CustomEncodingDeserializer;
import tools.jackson.dataformat.avro.schema.AvroSchemaHelper;
import tools.jackson.dataformat.avro.ser.CustomEncodingSerializer;

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
 * <li>{@link Union @Union} - Alias for <code>JsonSubTypes</code></li>
 * </ul>
 */
public class AvroAnnotationIntrospector extends AnnotationIntrospector
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    public AvroAnnotationIntrospector() { }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean hasIgnoreMarker(MapperConfig<?> config, AnnotatedMember m) {
        return _findAnnotation(m, AvroIgnore.class) != null;
    }

    @Override
    public PropertyName findNameForSerialization(MapperConfig<?> config, Annotated a) {
        return _findName(a);
    }

    @Override
    public PropertyName findNameForDeserialization(MapperConfig<?> config, Annotated a) {
        return _findName(a);
    }

    @Override
    public Object findDeserializer(MapperConfig<?> config, Annotated am) {
        AvroEncode ann = _findAnnotation(am, AvroEncode.class);
        if (ann != null) {
            return new CustomEncodingDeserializer<>((CustomEncoding<?>)ClassUtil.createInstance(ann.using(), true));
        }
        return null;
    }

    @Override
    public String findPropertyDefaultValue(MapperConfig<?> config, Annotated m) {
        AvroDefault ann = _findAnnotation(m, AvroDefault.class);
        return (ann == null) ? null : ann.value();
    }

    @Override
    public List<PropertyName> findPropertyAliases(MapperConfig<?> config, Annotated m) {
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
    public Boolean hasRequiredMarker(MapperConfig<?> config, AnnotatedMember m) {
        if (_hasAnnotation(m, Nullable.class)) {
            return Boolean.FALSE;
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
    public Object findSerializer(MapperConfig<?> config, Annotated a) {
        if (a.hasAnnotation(Stringable.class)) {
            return ToStringSerializer.class;
        }
        AvroEncode ann = _findAnnotation(a, AvroEncode.class);
        if (ann != null) {
            return new CustomEncodingSerializer<>((CustomEncoding<?>)ClassUtil.createInstance(ann.using(), true));
        }
        return null;
    }

    @Override
    public List<NamedType> findSubtypes(MapperConfig<?> config, Annotated a)
    {
        Class<?>[] types = _getUnionTypes(a);
        if (types == null) {
            return null;
        }
        ArrayList<NamedType> names = new ArrayList<>(types.length);
        for (Class<?> subtype : types) {
            names.add(new NamedType(subtype, AvroSchemaHelper.getTypeId(subtype)));
        }
        return names;
    }

    /* 13-Mar-2018, tatu: Kludge, hacky; should probably be refactored. But works as well
     *   as it used to, for the moment, so defer until later point.
     */
    @Override
    public Object findTypeResolverBuilder(MapperConfig<?> config, Annotated ann) {
        // 14-Apr-2017, tatu: There are two ways to enable polymorphic typing, above and beyond
        //    basic Jackson: use of `@Union`, and "default typing" approach for `java.lang.Object`:
        //    latter since Avro support for "untyped" values is otherwise difficult.
        //    This seems to work for now, but maybe needs more work in future...
        Class<?> raw = ann.getRawType();
        
        if ((raw == Object.class) || (_getUnionTypes(ann) != null)) {
            return AvroTypeResolverBuilder.construct(null);
            /*
            return AvroTypeResolverBuilder.construct(
                    JsonTypeInfo.Value.construct(JsonTypeInfo.Id.CUSTOM, // could be NONE, but there is type discriminator in Avro...
                    JsonTypeInfo.As.PROPERTY, // N/A for custom
                    "@class", // similarly, N/A
                    null, // defaultImpl
                    false));
                    */
        }
        return null;
    }

    protected Class<?>[] _getUnionTypes(Annotated a) {
        Union ann = _findAnnotation(a, Union.class);
        if (ann != null) {
            // 14-Apr-2017, tatu: I think it makes sense to require non-empty List, as this allows
            //   disabling annotation with overrides. But one could even consider requiring more than
            //   one (where single type is not really polymorphism)... for now, however, just one
            //   is acceptable, and maybe that has valid usages.
            Class<?>[] c = ann.value();
            if (c.length > 0) {
                return c;
            }
        }
        return null;
    }
}
