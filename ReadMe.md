# CLI-based generator of KLIB projects for benchmarks

## How to generate a single multi-module Gradle project
```
java -cp MainKt \
  --output-dir <outputDir> \
  --generation-mode single-gradle-project\
  --number-of-libraries 100 \
  --cinterop-libraries 10 \
  --declarations-per-library 5 \
  --dependencies-per-library 3 \
  --unique-packages 20
  --kotlin-version 2.4.0
```

How to build the generated libraries and the application:
```
cd <outputDir> && ./build-all.sh
```

Note: If you experience lots of GC during the build, or even OOM, please try adjusting JVM heap size settings.

Edit `<outputDir>/gradle.properties` and add something like this:
```
org.gradle.jvmargs=-Xmx32g
```

## How to generate separate Gradle projects

Sometimes, with high numbers of used libraries, it makes sense to generate every library in a separate project
rather than trying to combine everything together in a single multi-module Gradle build.
```
java -cp MainKt \
  --output-dir <outputDir> \
  --generation-mode separate-gradle-projects\
  --number-of-libraries 5000 \
  --cinterop-libraries 500 \
  --declarations-per-library 5 \
  --dependencies-per-library 3 \
  --unique-packages 1000
  --kotlin-version 2.4.0
```

How to build and publish the generated libraries to Maven local:
```
cd <outputDir> && ./build-libs.sh
```

After the libraries are published, you can build the application by running:
```
cd <outputDir> && ./build-app.sh
```

Note: If you experience lots of GC during the build, or even OOM, please try adjusting JVM heap size settings.

Edit `<outputDir>/app/gradle.properties` and add something like this:
```
org.gradle.jvmargs=-Xmx32g
```

## Samples
You can find some samples in the [samples](samples) directory.