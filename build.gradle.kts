plugins {
    `java-library`
    signing
    `maven-publish`
    kotlin("jvm") version "2.2.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
}

group = "io.javalin.community.graphql"
version = "7.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("library") {
            pom {
                name.set("javalin-graphql")
                description.set("Javalin GraphQL Plugin | Serve a GraphQL schema, GraphiQL and subscriptions under a dedicated endpoint")
                url.set("https://github.com/javalin/javalin-graphql")

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
                        email.set("contact@agustibm.com")
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
                username = System.getenv("MAVEN_NAME") ?: findProperty("mavenUser")?.toString() ?: ""
                password = System.getenv("MAVEN_TOKEN") ?: findProperty("mavenPassword")?.toString() ?: ""
            }
        }
    }
}

signing {
    if (findProperty("signing.keyId") != null) {
        sign(publishing.publications.getByName("library"))
    }
}

// Javalin 7 has a JVM 17 baseline. We compile with whatever JDK is on the machine
// (>= 17) but always *emit* 17 bytecode, so the artifact stays usable on 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    // All of these appear in this plugin's public API, so they belong on the consumer's
    // compile classpath: Javalin's Context and WsMessageContext in the builder and the
    // options, graphql-kotlin's GraphQLContextFactory, KotlinDataLoader and TopLevelObject
    // in public signatures (which drag in graphql-java's GraphQLContext), the schema
    // annotations a consumer needs to write resolvers at all, and Flow, which is what a
    // subscription resolver returns. As `implementation` none of it reached a consumer,
    // and the examples in the README did not compile.
    api("io.javalin", "javalin", "7.2.2")
    api("com.expediagroup", "graphql-kotlin-server", "10.2.0")
    api("com.expediagroup", "graphql-kotlin-schema-generator", "10.2.0")
    api("com.expediagroup", "graphql-kotlin-dataloader", "10.2.0")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.10.2")

    testImplementation("io.javalin", "javalin-testtools", "7.2.2")

    // Javalin 7 no longer bundles an object mapper; the app picks one. Tests need one to exist.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj", "assertj-core", "3.27.3")
    testImplementation("com.konghq", "unirest-java", "3.14.5")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
    testImplementation("org.java-websocket:Java-WebSocket:1.6.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// JPMS (issues #5 and #6). A full module-info.java is not possible yet: the
// graphql-kotlin artifacts declare neither a module descriptor nor an
// Automatic-Module-Name, so they resolve as automatic modules named after their
// file names (graphql.kotlin.server, ...). Requiring those would freeze unstable
// names into our descriptor, and Gradle refuses to put them on the module path at
// all. Declaring our own stable module name is the part we can do correctly: it
// lets a modular application `requires io.javalin.community.graphql` today.
tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.javalin.community.graphql")
    }
}

// Used by the deploy workflow to tell a snapshot from a release before publishing.
tasks.register("printVersion") {
    val projectVersion = version.toString()
    doLast { println(projectVersion) }
}
