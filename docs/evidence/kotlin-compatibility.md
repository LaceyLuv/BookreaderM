Source: https://github.com/JetBrains/kotlin-web-site/blob/3b022f0af1cbab7e9f4b63455a7fae73e6a2bcf8/docs/topics/gradle/gradle-configure-project.md
Git blob: 58530c9f21250fea67549d56da5f83d551d6cbbd

[//]: # (title: Configure a Gradle project)

To build a Kotlin project with [Gradle](https://docs.gradle.org/current/userguide/userguide.html), 
you need to [add the Kotlin Gradle plugin](#apply-the-plugin) to your build script file `build.gradle(.kts)` 
and [configure the project's dependencies](#configure-dependencies) there.

> To learn more about the contents of a build script,
> visit the [Explore the build script](get-started-with-jvm-gradle-project.md#explore-the-build-script) section.
>
{style="note"}

## Apply the plugin

To apply the Kotlin Gradle plugin, use the [`plugins{}` block](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block)
from the Gradle plugins DSL:

<tabs group="build-script">
<tab title="Kotlin" group-key="kotlin">

```kotlin
plugins {
    // Replace `<...>` with the plugin name appropriate for your target environment
    kotlin("<...>") version "%kotlinVersion%"
    // For example, if your target environment is JVM:
    // kotlin("jvm") version "%kotlinVersion%"
}
```

</tab>
<tab title="Groovy" group-key="groovy">

```groovy
plugins {
    // Replace `<...>` with the plugin name appropriate for your target environment
    id 'org.jetbrains.kotlin.<...>' version '%kotlinVersion%'
    // For example, if your target environment is JVM: 
    // id 'org.jetbrains.kotlin.jvm' version '%kotlinVersion%'
}
```

</tab>
</tabs>

> The Kotlin Gradle plugin (KGP) and Kotlin share the same version numbering.
>
{style="note"}

When configuring your project, check the Kotlin Gradle plugin (KGP) compatibility with available Gradle versions. 
In the following table, there are the minimum and maximum **fully supported** versions of Gradle and Android Gradle plugin (AGP):

| KGP version   | Gradle min and max versions           | AGP min and max versions                            |
|---------------|---------------------------------------|-----------------------------------------------------|
| 2.4.20        | %minGradleVersion%–%maxGradleVersion% | %minAndroidGradleVersion%–%maxAndroidGradleVersion% |
| 2.4.0-2.4.10  | 7.6.3–9.5.0                           | 8.5.2–9.1.0                                         |
| 2.3.20–2.3.21 | 7.6.3–9.3.0                           | 8.2.2–9.0.0                                         |
| 2.3.10        | 7.6.3–9.0.0                           | 8.2.2–9.0.0                                         |
| 2.3.0         | 7.6.3–9.0.0                           | 8.2.2–8.13.0                                        |
| 2.2.20–2.2.21 | 7.6.3–8.14                            | 7.3.1–8.11.1                                        |
| 2.2.0–2.2.10  | 7.6.3–8.14                            | 7.3.1–8.10.0                                        |
| 2.1.20–2.1.21 | 7.6.3–8.12.1                          | 7.3.1–8.7.2                                         |
| 2.1.0–2.1.10  | 7.6.3–8.10*                           | 7.3.1–8.7.2                                         |
| 2.0.20–2.0.21 | 6.8.3–8.8*                            | 7.1.3–8.5                                           |
| 2.0.0         | 6.8.3–8.5                             | 7.1.3–8.3.1                                         |
| 1.9.20–1.9.25 | 6.8.3–8.1.1                           | 4.2.2–8.1.0                                         |

> *Kotlin 2.0.20–2.0.21 and Kotlin 2.1.0–2.1.10 are fully compatible with Gradle up to 8.6.
> Gradle versions 8.7–8.10 are also supported, with only one exception: If you use the Kotlin Multiplatform Gradle plugin,
> you may see deprecation warnings in your multiplatform projects calling the `withJava()` function in the JVM target.
> For more information, see [Java source sets created by default](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#java-source-sets-created-by-default).
>
{style="warning"}

You can also use Gradle and AGP versions up to the latest releases, but if you do, keep in mind that you might encounter 
deprecation warnings or some new features might not work.

For example, the Kotlin Gradle plugin and the `kotlin-multiplatform` plugin %kotlinVersion% require the minimum Gradle
version of %minGradleVersion% for your project to compile.

Similarly, the maximum fully supported version is %maxGradleVersion%. It doesn't have deprecated Gradle
methods and properties, and supports all the current Gradle features.

### Earlier KGP versions {initial-collapse-state="collapsed" collapsible="true"}

| KGP version   | Gradle min and max versions           | AGP min and max versions                            |
|---------------|---------------------------------------|-----------------------------------------------------|
| 1.9.0–1.9.10  | 6.8.3–7.6.0                           | 4.2.2–7.4.0                                         |
| 1.8.20–1.8.22 | 6.8.3–7.6.0                           | 4.1.3–7.4.0                                         |      
| 1.8.0–1.8.11  | 6.8.3–7.3.3                           | 4.1.3–7.2.1                                         |   
| 1.7.20–1.7.22 | 6.7.1–7.1.1                           | 3.6.4–7.0.4                                         |
| 1.7.0–1.7.10  | 6.7.1–7.0.2                           | 3.4.3–7.0.2                                         |
| 1.6.20–1.6.21 | 6.1.1–7.0.2                           | 3.4.3–7.0.2                                         |

### Kotlin Gradle plugin data in a project

By default, the Kotlin Gradle plugin stores persistent project-specific data at the root of the project,
in the `.kotlin` directory.

> Do not commit the `.kotlin` directory to version control.
> For example, if you are using Git, add `.kotlin` to your project's `.gitignore` file.
>
{style="warning"}

There are properties you can add to the `gradle.properties` file of your project to configure this behavior:

| Gradle property                                     | Description                                                                                                                                       |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `kotlin.project.persistent.dir`                     | Configures the location where your project-level data is stored. Default: `<project-root-directory>/.kotlin`                                      |
| `kotlin.project.persistent.dir.gradle.disableWrite` | Controls whether writing Kotlin data to the `.gradle` directory is disabled (for backward compatibility with older IDEA versions). Default: false |

## Targeting the JVM

To target the JVM, apply the Kotlin JVM plugin.

<tabs group="build-script">
<tab title="Kotlin" group-key="kotlin">
