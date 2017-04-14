package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;

import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.NamedType;
import com.squareup.protoparser.DataType.ScalarType;
import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.FieldElement.Label;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.TypeElement;

public class MessageElementVisitor extends JsonObjectFormatVisitor.Base
    implements TypeElementBuilder
{
    protected MessageElement.Builder _builder;

    protected TagGenerator _tagGenerator;

    protected JavaType _type;

    protected Set<JavaType> _nestedTypes = new HashSet<>();

    protected DefinedTypeElementBuilders _definedTypeElementBuilders;

    public MessageElementVisitor(SerializerProvider provider, JavaType type,
            DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested)
    {
        super(provider);
        _definedTypeElementBuilders = definedTypeElementBuilders;
        _type = type;
        _builder = MessageElement.builder();
        _builder.name(type.getRawClass().getSimpleName());
        _builder.documentation("Message for " + type.toCanonical());
    }

    @Override
    public TypeElement build() {
        return _builder.build();
    }

    @Override
    public void property(BeanProperty writer) throws JsonMappingException {
        _builder.addField(buildFieldElement(writer, Label.REQUIRED));
    }

    @Override
    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void optionalProperty(BeanProperty writer) throws JsonMappingException {
        _builder.addField(buildFieldElement(writer, Label.OPTIONAL));
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) {
        throw new UnsupportedOperationException();
    }

    protected FieldElement buildFieldElement(BeanProperty writer, Label label) throws JsonMappingException
    {
        FieldElement.Builder fBuilder = FieldElement.builder();

        fBuilder.name(writer.getName());
        fBuilder.tag(nextTag(writer));

        JavaType type = writer.getType();

        if (type.isArrayType() || type.isCollectionLikeType()) {
            if (ProtobufSchemaHelper.isBinaryType(type)) {
                fBuilder.label(label);
                fBuilder.type(ScalarType.BYTES);
            } else {
                fBuilder.label(Label.REPEATED);
                fBuilder.type(getDataType(type.getContentType()));
            }
        } else {
            fBuilder.label(label);
            fBuilder.type(getDataType(type));
        }
        return fBuilder.build();
    }

    protected int nextTag(BeanProperty writer) {
        getTagGenerator(writer);
        return _tagGenerator.nextTag(writer);
    }

    protected void getTagGenerator(BeanProperty writer) {
        if (_tagGenerator == null) {
            if (ProtobufSchemaHelper.hasIndex(writer)) {
                _tagGenerator = new AnnotationBasedTagGenerator();
            } else {
                _tagGenerator = new DefaultTagGenerator();
            }
        }
    }

    protected DataType getDataType(JavaType type) throws JsonMappingException
    {
        if (!_definedTypeElementBuilders.containsBuilderFor(type)) { // No self ref
            if (isNested(type)) {
                if (!_nestedTypes.contains(type)) { // create nested type
                    _nestedTypes.add(type);
                    ProtoBufSchemaVisitor builder = acceptTypeElement(_provider, type,
                            _definedTypeElementBuilders, true);
                    DataType scalarType = builder.getSimpleType();
                    if (scalarType != null){
                        return scalarType;
                    }
                    _builder.addType(builder.build());
                }
            } else { // track non-nested types to generate them later
                ProtoBufSchemaVisitor builder = acceptTypeElement(_provider, type,
                        _definedTypeElementBuilders, false);
                DataType scalarType = builder.getSimpleType();
                if (scalarType != null){
                    return scalarType;
                }
            }
        }
        return NamedType.create(type.getRawClass().getSimpleName());
    }

    private ProtoBufSchemaVisitor acceptTypeElement(SerializerProvider provider, JavaType type,
            DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested) throws JsonMappingException
    {
        JsonSerializer<Object> serializer = provider.findValueSerializer(type, null);
        ProtoBufSchemaVisitor visitor = new ProtoBufSchemaVisitor(provider, definedTypeElementBuilders, isNested);
        serializer.acceptJsonFormatVisitor(visitor, type);
        return visitor;
    }

    private boolean isNested(JavaType type)
    {
        Class<?> match = type.getRawClass();
        for (Class<?> cls : _type.getRawClass().getDeclaredClasses()) {
            if (cls == match) {
                return true;
            }
        }
        return false;
    }
}
