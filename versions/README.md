# Minecraft versions

UniverseGate is built for several Minecraft versions from one codebase with
[Stonecutter](https://stonecutter.kikugie.dev/). Minecraft **1.21.1** is the primary version:
the code committed to git is always written for it.

## Layout

- `versions/<mc>.properties`: the dependencies of one Minecraft version (Minecraft, Fabric Loader,
  Fabric API, NeoForge, NeoForm, accepted version ranges). Its keys become project properties.
- `settings.gradle`: the list of supported versions (`minecraftVersions`).
- `stonecutter.gradle`: the root build script and the currently active version (`stonecutter.active`).
- Stonecutter creates one Gradle project per loader and version, for example `:fabric:1.21.1`
  in `fabric/versions/1.21.1/`. All of them use the loader's `build.gradle`. Jars land in
  `<loader>/versions/<mc>/build/libs/` and are named `universegate-<loader>-<mod version>+<mc>.jar`.

## Code that differs between versions

Use Stonecutter comments in Java (and JSON) files:

```java
//? if >=1.21.4 {
/*ItemStack stack = newApi();
*///?} else {
ItemStack stack = oldApi();
//?}
```

The active version's branch is plain code, the others are commented out. Stonecutter rewrites the
comments when you switch versions.

## Commands

- `./gradlew build`: build every loader for every version.
- `./gradlew :fabric:1.21.1:build`: one jar.
- `./gradlew :fabric:1.21.1:runClient`, `./gradlew :neoforge:1.21.1:runServer`: dev runs.
- `./gradlew "Set active project to <mc>"`: switch the sources to another version to work on it.
- `./gradlew "Reset active project"`: switch back to 1.21.1. **Do this before committing.**

## Adding a version

1. Copy `versions/1.21.1.properties` to `versions/<mc>.properties` and update the dependencies.
2. Add `<mc>` to `minecraftVersions` in `settings.gradle`.
3. Run `./gradlew :fabric:<mc>:build` and fix compile errors with `//?` conditions.
