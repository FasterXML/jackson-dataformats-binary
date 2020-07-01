# Overview

This is a multi-module umbrella project for [Jackson](../../../jackson)
standard binary dataformat backends.

Dataformat backends are used to support format alternatives to JSON, using
general-purpose Jackson  API. Formats included allow access using all 3
API styles (streaming, databinding, tree model).

For Jackson 2.x this is done by sub-classing Jackson core abstractions of:

* All backends sub-class `JsonFactory`, which is factory for:
    * `JsonParser` for reading data (decoding data encoding in supported format)
        * `JsonGenerator` for writing data (encoding data using supported format)
	* Some backends sub-class `ObjectMapper` for additional support for databinding

there will be some changes (such as introduction of format-specific `ObjectMapper`
sub-classes) in Jackson 3.0.

# Status

[![Build Status](https://travis-ci.org/FasterXML/jackson-dataformats-binary.svg)](https://travis-ci.org/FasterXML/jackson-dataformats-binary)


# Binary formats included

Currently included backends are:

* [Avro](avro/)
* [CBOR](cbor/)
* [Ion](ion/)
* [Protobuf](protobuf/)
* [Smile](smile/)

# License

All modules are licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

# Maven dependencies

To use these format backends Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-[FORMAT]</artifactId>
  <version>2.9.9</version>
</dependency>
```

where `[FORMAT]` is one of supported modules (`avro`, `cbor`, `smile` etc)

# Development

## Maintainers

* Author: Tatu Saloranta (@cowtowncoder)
* Active Maintainers:
    * Michael Liedtke (@mcliedtke) (Ion backend)

You may at-reference them as necessary but please keep in mind that all
maintenance work is strictly voluntary (no one gets paid to work on this
or any other Jackson components) so there is no guarantee for timeliness of
responses.

## Branches

`master` branch is for developing the next major Jackson version -- 3.0 -- but there
are active maintenance branches in which much of development happens:

* `2.12` is for developing the next 2.x version
* `2.11` are for backported fixes for 2.11 version

Older branches are usually not changed but are available for historic reasons.
All released versions have matching git tags (`jackson-dataformats-binary-2.10.3`).

Note that since individual format modules used to live in their own repositories,
older branches (before 2.8) and tags do not exist in this repository.

# Other Jackson binary backends

In addition to binary format backends hosted by FasterXML in this repo, there are other
known Jackson backends for binary data formats.
For example:

* [bson4jackson](https://github.com/michel-kraemer/bson4jackson) for [BSON](http://en.wikipedia.org/wiki/BSON)
* [EXIficient](https://github.com/EXIficient/exificient-for-json) for [Efficient XML Interchange](https://en.wikipedia.org/wiki/Efficient_XML_Interchange)
* [jackson-dataformat-msgpack](https://github.com/msgpack/msgpack-java/tree/develop/msgpack-jackson) for [MessagePack](http://en.wikipedia.org/wiki/MessagePack) (aka `MsgPack`) format

# More

See [Wiki](../../wiki) for more information (javadocs).
