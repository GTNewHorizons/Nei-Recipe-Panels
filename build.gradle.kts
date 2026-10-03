
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    useDependencyAccessTransformers.set(true)
}

tasks.test {
    useJUnitPlatform()
    classpath += sourceSets.main.get().compileClasspath
}
