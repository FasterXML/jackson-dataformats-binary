Here are people who have contributed to the development of Jackson JSON processor
binary data formats module
(version numbers in brackets indicate release in which the problem was fixed)

Tatu Saloranta (tatu.saloranta@iki.fi): author

--------------------------------------------------------------------------------
Credits for individual projects, since 2.8.0
--------------------------------------------------------------------------------

Michael Zeng (shotbythought@github)

* Contributed fix for #27: (protobuf) Fixed long deserialization problem for longs of ~13digit length
 (2.8.2)
* Reported #58 (avro): Regression due to changed namespace of inner enum types
 (2.8.8)

Kenji Noguchi (knoguchi@github)

* Reported #70 (protobuf), contributed fix: Can't deserialize packed repeated field
 (2.8.9)

marsqing@github

* Reported #85: (protobuf) _decode32Bits() bug in ProtobufParser
 (2.8.9)
* Reported #94: (protobuf) Should _ensureRoom in ProtobufGenerator.writeString()
 (2.8.10)
* Reported #106 (protobuf), contributed fix for: calling _skipUnknownValue() twice
 (2.8.11 / 2.9.1)
* Reported #116 (protobuf), contributed fix for: Should skip the positive byte
  which is the last byte of an varint
 (2.9.3)
* Reported #126, contributed fix for: always call checkEnd() when skip unknown field
 (2.8.11 / 2.9.3)

baharclerode@github:

* Contributed #14 (avro): Add support for Avro annotations via `AvroAnnotationIntrospector`
 (2.9.0)
* Contributed #15 (avro): Add a way to produce "file" style Avro output
 (2.9.0)
* Contributed #57 (avro): Add support for @Stringable annotation
 (2.9.0)
* Contributed #59 (avro): Add support for @AvroAlias annotation for Record/Enum name evolution
 (2.9.0)
* Contributed #60 (avro): Add support for `@Union` and polymorphic types
 (2.9.0)

Eldad Rudich (eldadru@github)

* Reported #68 (proto): Getting "type not supported as root type by protobuf" for serialization
  of short and UUID types
 (2.9.0)

philipa@github

* Reported #114 (cbor), contributed fix for: copyStructure(): avoid duplicate tags
  when copying tagged binary
 (2.9.3)

Jacek Lach (JacekLach@github)

* Reported #124: Invalid value returned for negative int32 where the absolute value is > 2^31 - 1
 (2.9.3)

Leo Wang (wanglingsong@github)

* Reported #135: Infinite sequence of `END_OBJECT` tokens returned at end of streaming read
 (2.9.6)

Michael Milkin (mmilkin@github)
* Reported, Contributed fix for #142: (ion) `IonParser.getNumberType()` returns `null`
  for `IonType.FLOAT`
 (2.9.7)

Guido Medina (guidomedina@github)
* Reported #153: (smile) Unable to set a compression input/output decorator to a `SmileFactory`
 (2.9.8)

Alexander Cyon (Sajjon@github)
* Reported #159: (cbor) Some short UTF Strings encoded using non-canonical form
 (2.9.9)

Łukasz Dziedziak (lukidzi@github)
* Reported, contributed fix for #161: (avro) Deserialize from newer version to older
   one throws NullPointerException
 (2.9.9)

Carter Kozak (cakofony@github)
* Reported, suggested fix for #155: Inconsistent support for FLUSH_PASSED_TO_STREAM
 (2.10.0)

Fernando Raganhan Barbosa (raganhan@github)
* Suggested #163: (ion) Update `ion-java` dependency
 (2.10.0)

Juliana Amorim (amorimjuliana@github)
* Reported #168: (avro) `JsonMappingException` for union types with multiple Record types
 (2.10.0)

Marcos Passos (marcospassos@github)
* Contributed fix for #168: (avro) `JsonMappingException` for union types with multiple Record types
 (2.10.0)
* Contributed fix for #173: (avro) Improve Union type serialization performance
 (2.10.0)
* Contributed fix for #211: (avro) Fix schema evolution involving maps of non-scalar
 (2.10.5)
* Contributed fix for #216: (avro) Avro null deserialization
 (2.11.2)
* Contributed #219: Cache record names to avoid hitting class loader
 (2.11.3)

John (iziamos@github)
* Reported, suggested fix for #178: Fix issue wit input offsets when parsing CBOR from `InputStream`
 (2.10.0)

Paul Adolph (padolph@github)
* Reported #185: (cbor) Internal parsing of tagged arrays can lead to stack overflow
 (2.10.1)
* Reported #186: (cbor) Eager allocation of byte buffer can cause `java.lang.OutOfMemoryError`
   exception
 (2.11.4)

Yanming Zhou (quaff@github)
* Reported #188: Unexpected `MismatchedInputException` for `byte[]` value bound to `String`
  in collection/array
 (2.10.1)

Zack Slayton (zslayton@github)
* Reported, contributed fix for #189: (ion) IonObjectMapper close()s the provided IonWriter unnecessarily
 (2.10.2)

Binh Tran (ankel@github)
* Reported, contributed fix for #192: (ion) Allow `IonObjectMapper` with class name annotation introspector
  to deserialize generic subtypes
 (2.11.0)

Jonas Konrad (yawkat@github)
* Reported, contributed fix for #201: `CBORGenerator.Feature.WRITE_MINIMAL_INTS` does not write
  most compact form for all integers
 (2.11.0)

Dylan (Quantum64@github)
* Reported #269: CBOR loses `Map` entries with specific `long` Map key values (32-bit boundary)
 (2.11.5 / 2.12.3)

Michael Liedtke (mcliedtke@github)

* Contributed fix for #212: (ion) Optimize `IonParser.getNumberType()` using
  `IonReader.getIntegerSize()`
 (2.12.0)
* Contributed #246: (ion) Add `IonObjectMapper.builderForBinaryWriters()` / `.builderforTextualWriters()`
  convenience methods
 (2.12.2)
 
Guillaume Bort (guillaumebort@github)

* Contributed implementation of #222: (cbor) Add `CBORGenerator.Feature.LENIENT_UTF_ENCODING`
  for lenient handling of Unicode surrogate pairs on writing
 (2.12.0)

Josh Barr (jobarr-amzn@github)

* Contributed #232: (ion) Allow disabling native type ids in IonMapper
 (2.12.1)

Fabian Meumertzheim (fmeum@github)

* Reported #236: `ArrayIndexOutOfBoundsException` in `CBORParser` for invalid UTF-8 String
 (2.12.2)
* Reported #257: (smile) Uncaught validation problem wrt Smile "BigDecimal" type
 (2.12.3)
* Reported #258: (smile) ArrayIndexOutOfBoundsException for malformed Smile header
 (2.12.3)
* Reported #259: (cbor) Failed to handle case of alleged String with length of
  Integer.MAX_VALUE
 (2.12.3)
* Reported #260: (smile) Allocate byte[] lazily for longer Smile binary data payloads
 (2.12.3)
* Reported #261 (cbor) CBORParser need to validate zero-length byte[] for BigInteger
 (2.12.3)
* Reported #263 (smile) Handle invalid chunked-binary-format length gracefully
 (2.12.3)
* Reported #266: (smile)  ArrayIndexOutOfBoundsException in SmileParser._decodeShortUnicodeValue()
 (2.12.3)
* Reported #268: (smile) Handle sequence of Smile header markers without recursion
 (2.12.3)
* Reported #272: (cbor) Uncaught exception in CBORParser._nextChunkedByte2 (by ossfuzzer)	
 (2.13.0)

(jhhladky@github)

* Contributed #241: (ion) Respect `WRITE_ENUMS_USING_TO_STRING` in `EnumAsIonSymbolSerializer`
 (2.12.2)

Nick (manaigrn-amzn@github)

* Contributed #270: (ion) Ion Polymorphic deserialization in 2.12 breaks wrt use of
  Native Type Ids when upgrading from 2.8
 (2.12.3)
