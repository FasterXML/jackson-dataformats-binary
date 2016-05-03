package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import com.fasterxml.jackson.databind.BeanProperty;

interface TagGenerator {
	int nextTag(BeanProperty writer);
}
