package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.NamedType;
import com.squareup.protoparser.DataType.ScalarType;
import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.FieldElement.Label;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.TypeElement;

public class MessageElementVisitor extends JsonObjectFormatVisitor.Base implements TypeElementBuilder
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

        _definedTypeElementBuilders.AddTypeElement(type, this, isNested);
    }

    @Override
    public TypeElement build() {
        return _builder.build();
    }

    @Override
    public void property(BeanProperty writer) throws JsonMappingException {
        FieldElement fElement = buildFieldElement(writer, Label.REQUIRED);
        _builder.addField(fElement);
    }

    @Override
    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) { }

    @Override
    public void optionalProperty(BeanProperty writer) throws JsonMappingException {
        FieldElement fElement = buildFieldElement(writer, Label.OPTIONAL);
        _builder.addField(fElement);
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) { }

    protected FieldElement buildFieldElement(BeanProperty writer, Label label) throws JsonMappingException
    {
        FieldElement.Builder fBuilder = FieldElement.builder();

        fBuilder.name(writer.getName());
        fBuilder.tag(nextTag(writer));

        JavaType type = writer.getType();

        if (type.isArrayType() || type.isCollectionLikeType()) {
            fBuilder.label(Label.REPEATED);
            fBuilder.type(getDataType(type.getContentType()));
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
            if (ProtobuffSchemaHelper.hasIndex(writer)) {
                _tagGenerator = new AnnotationBasedTagGenerator();
            } else {
                _tagGenerator = new DefaultTagGenerator();
            }
        }
    }

    protected DataType getDataType(JavaType type) throws JsonMappingException {
        ScalarType sType = ProtobuffSchemaHelper.getScalarType(type);
        if (sType != null) { // Is scalar type ref
            return sType;
        }

        if (!_definedTypeElementBuilders.containsBuilderFor(type)) { // No self ref
            if (Arrays.asList(_type.getRawClass().getDeclaredClasses()).contains(type.getRawClass())) { // nested
                if (!_nestedTypes.contains(type)) { // create nested type
                    _nestedTypes.add(type);
                    TypeElementBuilder nestedTypeBuilder = ProtobuffSchemaHelper.acceptTypeElement(_provider, type,
                            _definedTypeElementBuilders, true);

                    _builder.addType(nestedTypeBuilder.build());
                }
            } else { // tracking non-nested types to generate them later
                ProtobuffSchemaHelper.acceptTypeElement(_provider, type, _definedTypeElementBuilders, false);
            }
        }
        return NamedType.create(type.getRawClass().getSimpleName());
    }
}
