package com.fasterxml.jackson.dataformat.avro.java8;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.dataformat.avro.AvroModule;

public class AvroJavaTimeModule extends AvroModule {


  public AvroJavaTimeModule() {
    withAnnotationIntrospector(AvroJavaTimeAnnotationIntrospector.INSTANCE);
  }


  @Override
  public Version version() {
    return PackageVersion.VERSION;
  }


}
