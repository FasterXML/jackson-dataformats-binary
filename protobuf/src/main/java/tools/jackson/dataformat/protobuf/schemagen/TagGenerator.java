package tools.jackson.dataformat.protobuf.schemagen;

import tools.jackson.databind.BeanProperty;

interface TagGenerator {
	int nextTag(BeanProperty writer);
}
