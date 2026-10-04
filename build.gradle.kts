
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    useDependencyAccessTransformers.set(true)
}

tasks.test {
    useJUnitPlatform { excludeTags("gpu") }
    classpath += sourceSets.main.get().compileClasspath
}

tasks.register<Test>("gpuTest") {
    group = "verification"
    description = "Checks panel pixels and render-state restoration using an OpenGL context."
    dependsOn("extractNatives2")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform { includeTags("gpu") }
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    systemProperty("java.library.path", file("run/natives/lwjgl2").absolutePath)
}

if (providers.gradleProperty("profilePanels").isPresent) {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runClient")) {
            jvmArgs("-Dangelica.tracy=true", "-Dangelica.tracy.fineZones=true")
        }
    }
}
