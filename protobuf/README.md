## Overview

Project contains [Jackson](../../../jackson) extension component
for reading and writing [Protobuf](http://code.google.com/p/protobuf/) encoded data (see
[protobuf encoding spec](https://developers.google.com/protocol-buffers/docs/encoding)).
This project adds necessary abstractions on top to make things work with other Jackson functionality;
mostly just low-level Streaming components (`JsonFactory`, `JsonParser`, `JsonGenerator`).

Project is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

## Dependencies

Protoc (protobuf IDL) parsing is done using [square/protoparser](https://github.com/square/protoparser) library.
(note: another library, [Protostuff](http://code.google.com/p/protostuff/), also has usable parser)

Project does NOT depend on the official [Google Java protobuf](https://github.com/google/protobuf) library, although
it may be used for conformance testing purposes in future.

Protobuf module requires Java 7.

# Status

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-protobuf/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-protobuf/)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-protobuf/badge.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-protobuf)

With release 2.6.0 (Jul-2015), this module is considered stable, although number of production deployments is still limited.

# Functionality

## Supported Versions

Version 2 of `protoc` supported. This is the official standard used in production as of Aug-2015.
There is work underway by protobuf authors to specify a new version, called 'v3', but it has
not yet been finalized, although draft versions exist.

Protoc schema parser supports v3 as well (that is, protoc schemas
can be read in), but encoder/decoder does not support new v3 features.
This means that module will not encode (write) data using v3 constructs, nor be able to decode (read)
v3 encoded data.

When v3 specification is finalized we are likely to work on upgrading module to support v3; either
as option of this module, or via new v3-based module, depending on exact compatibility details
between v2 and v3.

# Usage

## Creating ObjectMapper
Usage is as with basic ```JsonFactory```; most commonly you will just construct a standard ObjectMapper with ```com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory```, like so:
```java
ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
```
Or the easy way, like:
```java
ObjectMapper mapper = new ProtobufMapper();
```

## Reading Protobuf Data

Assuming you have the following protobuf definition:,
```java
String protobuf_str = "message Employee {\n"
  +" required string name = 1;\n"
  +" required int32 age = 2;\n"
  +" repeated string emails = 3;\n"
  +" optional Employee boss = 4;\n"
+"}\n";

ProtobufSchema schema = ProtobufSchemaLoader.std.parse(protobuf_str);
```
and a POJO definition like:
```java
public class Employee
{
    public String name;
    public int age;
    public String[] emails;
    public Employee boss;
}
```
you can actually use data-binding like so:
```java
byte[] protobufData = ... ; // or find an InputStream
Employee empl = mapper.readerFor(Employee.class)
   .with(schema)
   .readValue(protobufData);
```

## Writing Protobuf Data

Writing protobuf-encoded data follows similar pattern:
```java
byte[] protobufData = mapper.writer(schema)
   .writeValueAsBytes(empl);
```
and that's about it.

## Generating Protobuf Schema From POJO Definition
You do not have to start with a protobuf Schema. This module can actually generate schemas for you, starting with POJO definitions! Here's how:
```java
public class POJO {
  // your typical, Jackson-compatible POJO (with or without annotations)
}

ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
mapper.acceptJsonFormatVisitor(POJO.class, gen);
ProtobufSchema schemaWrapper = gen.getGeneratedSchema();
NativeProtobufSchema nativeProtobufSchema = schemaWrapper.getSource();

String asProtofile = nativeProtobufSchema.toString();
```
Alternatively same can be achieved with less code:
```java
ProtobufMapper mapper = new ProtobufMapper()
ProtobufSchema schemaWrapper = mapper.generateSchemaFor(POJO.class)
NativeProtobufSchema nativeProtobufSchema = schemaWrapper.getSource();

String asProtofile = nativeProtobufSchema.toString();
```

# Missing features/Issues

Following features are not yet fully implemented as of version 2.6, but are planned to be evetually supported;

* Enforcing of mandatory values
* Value defaulting
* Construction of `protoc` schemas using alternatives to reading textual definition; for example, programmatic construction.

