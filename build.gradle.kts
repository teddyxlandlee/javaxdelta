buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.2.2")
    }
}

plugins {
    java
    `maven-publish`
    idea
    //`java-platform`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

group = "xland.ioutils.com.nothome"
version = "3.0.2"

java {
    withSourcesJar()
    //withJavadocJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

object Constants {
    const val MAIN_CLASS = "xland.ioutils.xdelta.wrapper.JarPatcherMain"
    const val GENERATOR_CLASS = "xland.ioutils.xdelta.wrapper.DeltaGenerator"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to Constants.MAIN_CLASS)
    }
}

val proguardDir : File = buildDir.resolve("proguard")
proguardDir.mkdir()
val proguardOutput : File = proguardDir.resolve("${project.name}-${project.version}-proguard.jar")
val proguardGeneratorOutput : File = proguardDir.resolve("${project.name}-${project.version}-proguard-generator.jar")

val mappingFile = proguardDir.resolve("proguard-${version}.mapping")
val generatorMappingFile = proguardDir.resolve("generator-${version}.mapping")

val proguardJar by tasks.registering(proguard.gradle.ProGuardTask::class) {
    moreConf()
    outjars(proguardOutput)
    keepclasseswithmembers("public class ${Constants.MAIN_CLASS} {\npublic static void main(java.lang.String[]);\n}")
    printmapping(mappingFile)
}

val proguardGeneratorJar by tasks.registering(proguard.gradle.ProGuardTask::class) {
    moreConf()
    outjars(proguardGeneratorOutput)
    keepclasseswithmembers("public class ${Constants.GENERATOR_CLASS} {\npublic static void main(java.lang.String[]);\n}")
    printmapping(generatorMappingFile)
}

fun proguard.gradle.ProGuardTask.moreConf() {
    dependsOn(tasks.jar)
    injars(tasks.jar)
    libraryjars(
        File(System.getProperty("java.home"), if (JavaVersion.current().isJava9Compatible) {
            "jmods"
        } else {
            "lib/rt.jar"
        })
    )
    repackageclasses("xdelta")
    keepattributes("LineNumberTable,SourceFile")
    renamesourcefileattribute("SourceFile")
    configuration("rootconf.pro")
}

val mappingZip by tasks.registering(Zip::class) {
    dependsOn(proguardJar, proguardGeneratorJar)
    from(mappingFile)
    from(generatorMappingFile)
    destinationDirectory.set(proguardDir)
    archiveClassifier.set("mapping")
}

tasks.register("deployJar", Jar::class) {
    dependsOn(proguardGeneratorJar, proguardJar, mappingZip)

    doFirst {
    	from(zipTree(proguardGeneratorOutput))
    }
    from(proguardOutput) {
        rename { "META-INF/wrapper.jar" }
    }
    from(mappingZip) {
        rename { "META-INF/mappings.zip" }
    }

    manifest {
        attributes(
            "Main-Class" to "xland.ioutils.xdelta.wrapper.DeltaGenerator",
            "Git-Repository" to "https://github.com/teddyxlandlee/javaxdelta",
            "Implementation-License" to "MIT",
        )
    }
    archiveClassifier.set("generator")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
