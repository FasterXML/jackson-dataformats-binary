package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import com.fasterxml.jackson.databind.BeanProperty;

public class AnnotationBasedTagGenerator implements TagGenerator
{
    @Override
    public int nextTag(BeanProperty writer) {
        Integer ix = writer.getMetadata().getIndex();
        if (ix != null) {
            return ix.intValue();
        }
        throw new IllegalStateException("No index metadata found for " + writer.getFullName()
                + " (usually annotated with @JsonProperty.index): either annotate all properties of type " + writer.getWrapperName().getSimpleName() + " with indexes or none at all");
    }
}
