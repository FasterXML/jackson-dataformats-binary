package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.CustomEncodingDeserializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroDateDateDeserializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroDateTimeDeserializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroDateTimestampDeserializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroDecimalDeserializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroUUIDDeserializer;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper;
import com.fasterxml.jackson.dataformat.avro.ser.AvroBytesDecimalSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.AvroDateDateSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.AvroDateTimeSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.AvroDateTimestampSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.AvroFixedDecimalSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.AvroUUIDSerializer;
import com.fasterxml.jackson.dataformat.avro.ser.CustomEncodingSerializer;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.AvroIgnore;
import org.apache.avro.reflect.AvroName;
import org.apache.avro.reflect.CustomEncoding;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.Stringable;
import org.apache.avro.reflect.Union;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Adds support for the following annotations from the Apache Avro implementation:
 * <ul>
 * <li>{@link AvroIgnore @AvroIgnore} - Alias for <code>JsonIgnore</code></li>
 * <li>{@link AvroName @AvroName("custom Name")} - Alias for <code>JsonProperty("custom name")</code></li>
 * <li>{@link AvroDefault @AvroDefault("default value")} - Alias for <code>JsonProperty.defaultValue</code>, to
 * define default value for generated Schemas
 * </li>
 * <li>{@link Nullable @Nullable} - Alias for <code>JsonProperty(required = false)</code></li>
 * <li>{@link Stringable @Stringable} - Alias for <code>JsonCreator</code> on the constructor and <code>JsonValue</code> on
 * the {@link #toString()} method. </li>
 * <li>{@link Union @Union} - Alias for <code>JsonSubTypes</code></li>
 * </ul>
 *
 * @since 2.9
 */
public class AvroAnnotationIntrospector extends AnnotationIntrospector {
  private static final long serialVersionUID = 1L;

  public AvroAnnotationIntrospector() {
  }

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
  public Object findDeserializer(Annotated a) {
    AvroEncode ann = _findAnnotation(a, AvroEncode.class);
    if (ann != null) {
      return new CustomEncodingDeserializer<>((CustomEncoding<?>) ClassUtil.createInstance(ann.using(), true));
    }

    AvroType logicalType = _findAnnotation(a, AvroType.class);

    if(null != logicalType) {
      switch (logicalType.logicalType()) {
        case DECIMAL:
          if (a.getRawType().isAssignableFrom(BigDecimal.class)) {
            return new AvroDecimalDeserializer(logicalType.scale());
          }
        case TIME_MILLISECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimeDeserializer.MILLIS;
          }
        case TIMESTAMP_MILLISECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimestampDeserializer.MILLIS;
          }
        case TIME_MICROSECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimeDeserializer.MICROS;
          }
        case TIMESTAMP_MICROSECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimestampDeserializer.MICROS;
          }
        case UUID:
          if (a.getRawType().isAssignableFrom(UUID.class)) {
            return AvroUUIDDeserializer.INSTANCE;
          }
        case DATE:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateDateDeserializer.INSTANCE;
          }
      }
    }
    return null;
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

  protected PropertyName _findName(Annotated a) {
    AvroName ann = _findAnnotation(a, AvroName.class);
    return (ann == null) ? null : PropertyName.construct(ann.value());
  }

  @Override
  public Boolean hasRequiredMarker(AnnotatedMember m) {
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
  public Object findSerializer(Annotated a) {
    if (a.hasAnnotation(Stringable.class)) {
      return ToStringSerializer.class;
    }
    AvroEncode ann = _findAnnotation(a, AvroEncode.class);
    if (ann != null) {
      return new CustomEncodingSerializer<>((CustomEncoding<?>) ClassUtil.createInstance(ann.using(), true));
    }
    AvroType logicalType = _findAnnotation(a, AvroType.class);
    
    if(null != logicalType) {
      switch (logicalType.logicalType()) {
        case DECIMAL:
          switch (logicalType.schemaType()) {
            case FIXED:
              if (a.getRawType().isAssignableFrom(BigDecimal.class)) {
                return new AvroFixedDecimalSerializer(logicalType.scale(), logicalType.fixedSize());
              }
            case BYTES:
              if (a.getRawType().isAssignableFrom(BigDecimal.class)) {
                return new AvroBytesDecimalSerializer(logicalType.scale());
              }
            default:
              throw new UnsupportedOperationException(
                  String.format("%s is not a supported type for the logical type 'decimal'", logicalType.schemaType())
              );
          }
        case TIME_MILLISECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimeSerializer.MILLIS;
          }
        case TIMESTAMP_MILLISECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimestampSerializer.MILLIS;
          }
        case TIME_MICROSECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimeSerializer.MICROS;
          }
        case TIMESTAMP_MICROSECOND:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateTimestampSerializer.MICROS;
          }
        case UUID:
          if (a.getRawType().isAssignableFrom(UUID.class)) {
            return AvroUUIDSerializer.INSTANCE;
          }
        case DATE:
          if (a.getRawType().isAssignableFrom(Date.class)) {
            return AvroDateDateSerializer.INSTANCE;
          }
      }
    }
    
    return null;
  }

  @Override
  public List<NamedType> findSubtypes(Annotated a) {
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

  @Override
  public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config, AnnotatedClass ac, JavaType baseType) {
    return _findTypeResolver(config, ac, baseType);
  }

  @Override
  public TypeResolverBuilder<?> findPropertyTypeResolver(MapperConfig<?> config, AnnotatedMember am, JavaType baseType) {
    return _findTypeResolver(config, am, baseType);
  }

  @Override
  public TypeResolverBuilder<?> findPropertyContentTypeResolver(MapperConfig<?> config, AnnotatedMember am, JavaType containerType) {
    return _findTypeResolver(config, am, containerType);
  }

  protected TypeResolverBuilder<?> _findTypeResolver(MapperConfig<?> config, Annotated ann, JavaType baseType) {
    // 14-Apr-2017, tatu: There are two ways to enable polymorphic typing, above and beyond
    //    basic Jackson: use of `@Union`, and "default typing" approach for `java.lang.Object`:
    //    latter since Avro support for "untyped" values is otherwise difficult.
    //    This seems to work for now, but maybe needs more work in future...
    if (baseType.isJavaLangObject() || (_getUnionTypes(ann) != null)) {
      TypeResolverBuilder<?> resolver = new AvroTypeResolverBuilder();
      JsonTypeInfo typeInfo = ann.getAnnotation(JsonTypeInfo.class);
      if (typeInfo != null && typeInfo.defaultImpl() != JsonTypeInfo.class) {
        resolver = resolver.defaultImpl(typeInfo.defaultImpl());
      }
      return resolver;
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
