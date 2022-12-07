/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    java
    `maven-publish`
    idea
}

repositories {
    mavenCentral()
}

dependencies {
    //implementation("trove:trove:2.1.1")
}

group = "xland.ioutils.com.nothome"
version = "2.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_1_5
    targetCompatibility = JavaVersion.VERSION_1_5
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
