plugins {
    java
    application
}

group = "io.aquadecomp"
version = "1.0.0"

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
    implementation("org.benf:cfr:0.152")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")
    implementation("org.vineflower:vineflower:1.11.1")
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.38")
    
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("org.ow2.asm:asm:9.6")
    
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("commons-io:commons-io:2.13.0")
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.aquadecomp.Aquadecomp")
}

tasks.jar {
    archiveBaseName.set("aquedecomp")
    archiveVersion.set("")
    archiveFileName.set("aquedecomp.jar")
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
