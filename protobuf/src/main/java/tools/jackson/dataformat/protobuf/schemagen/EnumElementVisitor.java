package tools.jackson.dataformat.protobuf.schemagen;

import java.util.Set;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor.Base;
import com.squareup.protoparser.EnumConstantElement;
import com.squareup.protoparser.EnumElement;
import com.squareup.protoparser.TypeElement;

public class EnumElementVisitor extends Base implements TypeElementBuilder {

	EnumElement.Builder _builder;

	DefaultTagGenerator _tagGenerator = new DefaultTagGenerator(0);

	public EnumElementVisitor(SerializationContext provider, JavaType type,
			DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested) {

		if (!type.isEnumType()) {
			throw new IllegalArgumentException("Expected an enum, however given type is " + type);
		}

		_builder = EnumElement.builder();
		_builder.name(type.getRawClass().getSimpleName());
		_builder.documentation("Enum for " + type.toCanonical());
	}

	@Override
	public TypeElement build() {
		return _builder.build();
	}

	@Override
	public void enumTypes(Set<String> enums) {
		for (String eName : enums) {
			_builder.addConstant(buildEnumConstant(eName));
		}
	}

	protected EnumConstantElement buildEnumConstant(String name) {
		EnumConstantElement.Builder builder = EnumConstantElement.builder();
		builder.name(name);
		builder.tag(_tagGenerator.nextTag());
		return builder.build();
	}
}
