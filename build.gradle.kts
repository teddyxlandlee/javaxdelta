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
version = "2.10.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    //withJavadocJar()
}

object Constants {
    const val MAIN_CLASS = "xland.ioutils.xdelta.wrapper.JarPatcherMain"
}

tasks.jar {
    manifest {
        attributes("Main-Class" to Constants.MAIN_CLASS)
    }
}

val proguardOutput : File = buildDir.resolve("libs/${project.name}-${project.version}-proguard.jar")

val proguardJar by tasks.registering(proguard.gradle.ProGuardTask::class) {
    dependsOn("jar")
    injars(tasks.jar)
    outjars(proguardOutput)
    libraryjars(
        File(System.getProperty("java.home"), if (JavaVersion.current().isJava9Compatible) {
            "jmods"
        } else {
            "lib/rt.jar"
        })
    )

    repackageclasses("xdelta")
    keepclasseswithmembers("public class ${Constants.MAIN_CLASS} {\npublic static void main(java.lang.String[]);\n}")
    keepattributes("LineNumberTable,SourceFile")
    renamesourcefileattribute()
    configuration("rootconf.pro")
    printmapping(buildDir.resolve("proguard-${version}.mapping"))
}

tasks.register("deployJar", Jar::class) {
    dependsOn(tasks.jar, proguardJar)
    from(zipTree(tasks.jar.get().archiveFile))
    from(proguardOutput) {
        rename { "META-INF/wrapper.jar" }
    }
    manifest {
        attributes("Main-Class" to "xland.ioutils.xdelta.wrapper.DeltaGenerator")
    }
    archiveClassifier.set("generator")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
