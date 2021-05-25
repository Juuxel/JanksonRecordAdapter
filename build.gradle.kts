plugins {
    `java-library`
    `maven-publish`
    signing
    id("org.cadixdev.licenser") version "0.6.0"
}

group = "io.github.juuxel"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16

    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jankson)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

license {
    header(file("HEADER.txt"))
}

tasks {
    withType<JavaCompile> {
        options.release.set(16)
        options.encoding = "UTF-8"
    }

    javadoc {
        options {
            this as StandardJavadocDocletOptions

            charSet = "UTF-8"
            encoding = "UTF-8"
            docEncoding = "UTF-8"

            linkSource()
            links("https://javadoc.io/doc/blue.endless/jankson/${libs.versions.jankson.get()}")
        }
    }

    jar {
        from(file("LICENSE"))
        manifest.attributes("Automatic-Module-Name" to "io.github.juuxel.recordadapter")
    }

    test {
        useJUnitPlatform()
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])

        pom {
            name.set(base.archivesBaseName)
            description.set("A Jankson (de)serialiser for records")
            url.set("https://github.com/Juuxel/JanksonRecordAdapter")

            licenses {
                license {
                    name.set("Mozilla Public License Version 2.0")
                    url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                }
            }

            developers {
                developer {
                    id.set("Juuxel")
                    name.set("Juuxel")
                    email.set("juuzsmods@gmail.com")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/Juuxel/JanksonRecordAdapter.git")
                developerConnection.set("scm:git:ssh://github.com:Juuxel/JanksonRecordAdapter.git")
                url.set("https://github.com/Juuxel/JanksonRecordAdapter")
            }
        }
    }

    repositories {
        maven {
            name = "ossrh"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")

            credentials(PasswordCredentials::class)
        }
    }
}

if (project.hasProperty("signing.keyId")) {
    signing {
        sign(publishing.publications)
    }
}
