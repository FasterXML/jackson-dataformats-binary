package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import com.fasterxml.jackson.databind.BeanProperty;

public class DefaultTagGenerator implements TagGenerator {

	protected int _tagCounter;

	public DefaultTagGenerator() {
		this(1);
	}

	public DefaultTagGenerator(int startingTag) {
		_tagCounter = startingTag;
	}

	@Override
	public int nextTag(BeanProperty writer) {
		if (ProtobufSchemaHelper.hasIndex(writer)) {
			throw new IllegalStateException(writer.getFullName()
					+ " is annotated with 'JsonProperty.index', however not all properties of type "
					+ writer.getWrapperName().getSimpleName()
					+ " are annotated. Either annotate all properties or none at all.");
		}

		return nextTag();
	}
	
	public int nextTag() {
		return _tagCounter++;
	}

}
