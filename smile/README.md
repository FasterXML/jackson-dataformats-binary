## Overview

This Jackson extension handles reading and writing of data encoded in [Smile](https://github.com/FasterXML/smile-format-specification)
data format ("binary JSON").
It extends standard Jackson streaming API (`JsonFactory`, `JsonParser`, `JsonGenerator`), and as such works seamlessly with all the higher level data abstractions (data binding, tree model, and pluggable extensions).

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-smile/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.dataformat/jackson-dataformat-smile/)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-smile/badge.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.dataformat/jackson-dataformat-smile)

## Status

Module has been mature since Jackson 1.6, and is used widely ([over 150 projects](http://mvnrepository.com/artifact/com.fasterxml.jackson.dataformat/jackson-dataformat-smile)), often as a dynamically configurable alternative to textual JSON.

It also works very well for use cases like caching, especially when databinding is configured to
[serialize POJOs as Arrays](https://medium.com/@cowtowncoder/serializing-java-pojo-as-json-array-with-jackson-2-more-compact-output-510a85c019d4) which can further reduce size of serialized objects.

## Maven dependency

To use this module on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-smile</artifactId>
  <version>2.8.9</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

Basic usage is by using `SmileFactory` in places where you would usually use `JsonFactory`:

```java
ObjectMapper mapper = new ObjectMapper(new SmileFactory());
// (or can construct factory, configure instance with 'SmileParser.Feature'
// and 'SmileGenerator.Feature' first
SomeType value = ...;
byte[] smileData = mapper.writeValueAsBytes(value);
SomeType otherValue = mapper.readValue(smileData, SomeType.class);
```

## Related

* [Smile-format discussion group](https://groups.google.com/forum/#!forum/smile-format-discussion)
* Non-java Smile codecs (for interoperability):
    * C: [libsmile](https://github.com/pierre/libsmile) (with wrappers for Ruby, Perl)
    * Clojure: [Cheshire](https://github.com/dakrone/cheshire)
    * Javascript: [smile-js](https://github.com/ngyewch/smile-js)
    * Python [PySmile](https://github.com/jhosmer/PySmile)
