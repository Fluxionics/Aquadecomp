plugins {
    java
    application
}

group = "io.aquadecomp"
version = "0.1.0-beta"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/jadx/jadx-1.5.6-all.jar"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("com.formdev:flatlaf:3.7.2")
}

application {
    mainClass.set("com.aquadecomp.Aquadecomp")
}

tasks.jar {
    archiveBaseName.set("aquedecomp")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.aquadecomp.Aquadecomp"
        attributes["Implementation-Title"] = "Aquadecomp"
        attributes["Implementation-Version"] = project.version
    }
    from({
        configurations.runtimeClasspath.get()
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
