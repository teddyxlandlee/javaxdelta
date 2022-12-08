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
version = "2.1.1"

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

println("javaxdelta.input: ${if (project.ext.has("javaxdelta.input")) project.ext["javaxdelta.input"] else null}")

tasks.register("proguardJar", proguard.gradle.ProGuardTask::class) {
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

    //flattenpackagehierarchy("xdelta")
    repackageclasses("xdelta")
    keepclasseswithmembers("public class ${Constants.MAIN_CLASS} {\npublic static void main(java.lang.String[]);\n}")
    configuration("rootconf.pro")
    printmapping(buildDir.resolve("proguard-${version}.mapping"))
}

val prepareDeployThings = tasks.register("prepareDeployThings") {
    doLast {
        if (project.ext.has("javaxdelta.input")) {
            val inputFilename = project.ext["javaxdelta.input"].toString()
            this.temporaryDir.resolve("input-file").writeText(inputFilename)
        }
    }
}

tasks.register("deployJar", Zip::class) {
    destinationDirectory.set(buildDir.resolve("deploy").apply(File::mkdirs))
    //destinationDirectory.set(buildDir.resolve("libs"))
    if (!project.ext.has("javaxdelta.useCache")) {
        dependsOn("proguardJar")
        from(zipTree(proguardOutput))
    } else {
        from(zipTree(project.ext["javaxdelta.useCache"].toString()))
    }
    archiveClassifier.set("deploy")
    archiveExtension.set("jar")

    dependsOn(prepareDeployThings)
    from(prepareDeployThings.get().temporaryDir.resolve("input-file")) {
        into("META-INF")
    }
    if (project.ext.has("javaxdelta.delta")) {
        from(project.ext["javaxdelta.delta"].toString()) {
            rename { "META-INF/patch.bin" }
        }
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
