./gradlew dex
cp build/libs/Crystal.jar /sd
rm -rf /sd/gen
cp -r build/generated/sources/annotationProcessor/java/main/crystal/gen /sd
cp -r ~/crystal /sd
