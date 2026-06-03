# CLI-based generator of KLIB projects for benchmarks

## How to generate a single multi-module project for benchmarking
```
java -cp MainKt \
  --output-dir <outputDir> \
  --number-of-projects 100 \
  --cinterop-projects 10 \
  --declarations-per-project 5 \
  --dependencies-per-project 3 \
  --unique-packages 20
  --kotlin-version 2.4.0 \
```

How to build the generated project:
```
cd <outputDir> && ./build.sh
```

Note: If you experience lots of GC during the build, or even OOM, please try adjusting JVM heap size settings.

Edit `<outputDir>/gradle.properties` and add something like this:
```
org.gradle.jvmargs=-Xmx32g
```
## Samples
You can find some samples in the [samples](samples) directory.