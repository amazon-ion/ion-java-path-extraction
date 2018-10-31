## Ion Java Path Extraction

Ion Path Extraction API aims to combine the convenience of a DOM API with the speed of a streaming API.

The traditional streaming and DOM APIs force the user to choose between speed and convenience, respectively. 
Path extraction APIs aim to combine the two by allowing the user to register paths into the data using just a 
few lines of code and receive callbacks during stream processing when any of those paths is matched. This allows 
the Ion reader to plan the most efficient traversal over the data without requiring further manual interaction 
from the user. For example, there is no reason to step in to containers which could not possibly match one of 
the search paths. When encoded in binary Ion, the resulting skip is a seek forward in the input stream, which 
is inexpensive relative to the cost of parsing (and in the case of a DOM, materializing) the skipped value.

## Usage 
Path extractor works in two phases: 
1. Configuration
2. Notification  

### Search Paths
A `SearchPath` is a path provided to the extractor for matching. It's composed of a list of `PathComponent`s 
which can be one of: 
* Wildcard: matches all values.
* Index: match the value at that index.
* Text: match all values whose field names are equivalent to that text.

Some examples: 
```
data on reader: {foo: ["foo1", "foo2"] , bar: "myBarValue"}

(foo 0) - matches "foo1"
(1)     - matches "myBarValue"
(*)     - matches ["foo1", "foo2"] and "myBarValue"
()      - matches {foo: ["foo1", "foo2"] , bar: "myBarValue"}
```

### Configuration  
The configuration phase involves building a `PathExtractor` instance through the `PathExtractorBuilder` by setting its 
configuration options and registering its search paths. The built `PathExtractor` can be reused over many `IonReader`s.

example: 

```java 
PathExtractorBuilder.standard()
                    .withMatchCaseInsensitive(true) 
                    .withSearchPath("(foo)", (reader) -> { ... })
                    .build()
``` 

see `PathExtractorBuilder` javadoc for more information on configuration options and search path registration. 

### Notification  
Each time the `PathExtractor` encounters a value that matches a registered search path it will invoke the respective 
callback passing the reader positioned at the current value. See `PathExtractorBuilder#withSearchPath` methods for more 
information on the callback contract.

### Example: 

```java
// Capture all matched values into a List
final List<Integer> list = new ArrayList<>();
final Function<IonReader, Integer> callback = (reader) -> {
    list.add(reader.intValue());

    return 0; // See PathExtractorBuilder#withSearchPath javadoc for more details on the callback contract
};

final PathExtractor pathExtractor = PathExtractorBuilder.standard()
    .withSearchPath("(foo)", callback)
    .withSearchPath("(bar)", callback)
    .withSearchPath("(baz 1)", callback)
    .build();

final IonReader ionReader = IonReaderBuilder.standard().build("{foo: 1}"
        + "{bar: 2}"
        + "{baz: [10,20,30,40]}"
        + "{other: 99}"
);

pathExtractor.match(ionReader);

assertEquals("[1, 2, 20]", list.toString());
```

## Benchmark 

Some benchmarks comparing the path extractor with fully materializing a DOM are included in this package. All benchmarks
use as data source the JSON in https://data.nasa.gov/data.json, a publicly available data set from NASA. 

The `dataset` struct from the original JSON is written as Ion binary and Ion text without any type coercion. The 
binary file is ~81M and the text file ~95M. There are four benchmarks types: 
1. `dom`: fully materializes a DOM for the file using an `IonLoader`. 
1. `full`: fully materializes all struct fields as `IonValue`s using a path extractor.
1. `partial`: materializes a single struct fields as `IonValue` using a path extractor.
1. `partialNoDom`: access the java representation directly of a single struct field without materializing an `IonValue`.

There is a binary and a text version for all four benchmark types. See the `PathExtractorBenchmark` class for 
more details.
 
To execute the benchmarks run: `gradle --no-daemon jmh`, requires an internet connection as it downloads the data set. 
Results bellow, higher is better. 

```
Benchmark                                   Mode  Cnt   Score   Error  Units
PathExtractorBenchmark.domBinary           thrpt   10   1.128 ± 0.050  ops/s
PathExtractorBenchmark.domText             thrpt   10   0.601 ± 0.019  ops/s
PathExtractorBenchmark.fullBinary          thrpt   10   1.227 ± 0.014  ops/s
PathExtractorBenchmark.fullText            thrpt   10   0.665 ± 0.010  ops/s
PathExtractorBenchmark.partialBinary       thrpt   10  14.912 ± 0.271  ops/s
PathExtractorBenchmark.partialBinaryNoDom  thrpt   10  15.650 ± 0.297  ops/s
PathExtractorBenchmark.partialText         thrpt   10   1.343 ± 0.029  ops/s
PathExtractorBenchmark.partialTextNoDom    thrpt   10   1.307 ± 0.015  ops/s
```

Using the path extractor has equivalent performance for both text and binary when fully materializing the document and 
can give significant performance improvements when partially materializing binary documents. This happens due to Ion's 
ability to skip scan values in the binary format as they are length prefixed. The gains will be proportional to how 
much of the document can be skipped over.    

## Ion Developer information
See the developer guide on: http://amzn.github.io/ion-docs/guides/path-extractor-guide.html

## License
This library is licensed under the Apache 2.0 License. 
