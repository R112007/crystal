./gradlew dex
cp build/libs/Crystal.jar /storage/emulated/0/Android/data/io.anuke.mindustry/files/mods/
rm -rf /storage/emulated/0/Download/gen
cp -r build/generated/sources/annotationProcessor/java/main/crystal/gen /storage/emulated/0/Download
cp -r ~/crystal/src /storage/emulated/0/Download/crystal
