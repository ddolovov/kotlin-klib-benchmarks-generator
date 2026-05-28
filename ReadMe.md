# CLI-based generator of KLIB projects for benchmarks

How to generate a project:
```
java -cp MainKt \
  --kotlin-version 2.3.20 \
  --output-dir <outputDir> \
  --number-of-projects 100 \
  --cinterop-projects 20 \
  --declarations-per-project 10 \
  --dependencies-per-project 3 \
  --unique-packages 40
```

How to build the generated project:
```
cd <outputDir>
./gradlew :app:assemble
```