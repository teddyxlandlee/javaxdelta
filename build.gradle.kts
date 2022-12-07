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
version = "2.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    //withJavadocJar()
    manifest {
        attributes("Main-Class" to "xland.ioutils.xdelta.wrapper.JarPatcherMain")
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
