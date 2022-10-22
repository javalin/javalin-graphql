plugins {
    `java-library`
    signing
    `maven-publish`
    kotlin("jvm") version "1.7.0"
}

apply(plugin = "maven-publish")
apply(plugin = "java-library")
apply(plugin = "application")
apply(plugin = "signing")
apply(plugin = "org.jetbrains.kotlin.jvm")

group = "io.javalin.community.graphql"
version = "5.0.1"

repositories {
    mavenCentral()
    maven("https://maven.reposilite.com/snapshots")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            pom {
                name.set("javalin-grqphql")
                description.set("Javalin OpenAPI Plugin | Serve raw OpenApi documentation under dedicated endpoint")
                url.set("https://github.com/javalin/javalin-openapi")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("7agustibm")
                        name.set("Agusti Becerra Mila")
                        email.set("contact@agustibm.net")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/javalin/javalin-graphql.git")
                    developerConnection.set("scm:git:ssh://github.com/javalin/javalin-graphql.git")
                    url.set("https://github.com/javalin/javalin-graphql.git")
                }
            }

            from(components.getByName("java"))
        }
    }
    repositories {
        maven {
            name = "reposilite-repository"
            url = uri(
                "https://maven.reposilite.com/${
                    if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
                }"
            )

            credentials {
                username = System.getenv("MAVEN_NAME") ?: property("mavenUser").toString()
                password = System.getenv("MAVEN_TOKEN") ?: property("mavenPassword").toString()
            }
        }
    }
}

signing {
    if (findProperty("signing.keyId") != null) {
        sign(publishing.publications.getByName("library"))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation("io.javalin", "javalin", "5.0.1")
    implementation("com.expediagroup", "graphql-kotlin-server", "5.5.0")
    implementation("com.expediagroup", "graphql-kotlin-schema-generator", "5.5.0")

    testImplementation("io.javalin", "javalin-testtools", "5.0.1")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testImplementation("org.assertj", "assertj-core", "3.20.2")
    testImplementation("com.konghq", "unirest-java", "3.13.8")
    testImplementation("org.slf4j:slf4j-simple:2.0.0-alpha7")
    testImplementation("javax.servlet", "javax.servlet-api", "3.1.0")
    testImplementation("org.java-websocket:Java-WebSocket:1.5.3")
    testImplementation("io.projectreactor:reactor-core:3.4.19")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

fun getEnvOrProperty(env: String, property: String): String? =
    System.getenv(env) ?: findProperty(property)?.toString()
