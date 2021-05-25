# Jankson Record Adapter

An automatic record (de)serialiser for [Jankson](https://github.com/falkreon/Jankson).

## Dependency info

JRA is available on the Maven Central repository as `io.github.juuxel:jankson-record-adapter:1.0.0`.

## Usage

You can add a record adapter to a Jankson builder by using `RecordAdapter.registerFor`
or create and add one manually using `RecordAdapter.of`.

```java
import juuxel.recordadapter.RecordAdapter;

var janksonBuilder = Jankson.builder();
RecordAdapter.registerFor(janksonBuilder, MyRecord.class);

// Or separately
var adapter = RecordAdapter.of(MyRecord.class);
var jankson = Jankson.builder()
    .registerSerializer(MyRecord.class, adapter)
    .registerDeserializer(JsonObject.class, MyRecord.class, adapter)
    .build();
```

The record components are (de)serialised using the same marshaller,
so their serialisation can be controlled using the Jankson builder as well.

JRA also supports Jankson's `@Comment` and `@SerializedName` annotations:

```java
public record MyRecord(@SerializedName("different name") int x, @Comment("some comment") int y) {}

jankson.toJson(new MyRecord(1, 2));

// {
//   "different name": 1,
//   // some comment
//   "y": 2
// }
```
