// Временная проверка: настоящий обход Викитеки тем же парсером, что в приложении.
plugins {
    kotlin("jvm") version "2.0.21"
    application
}
repositories { mavenCentral() }
dependencies { implementation("org.json:json:20240303") }
sourceSets { main { kotlin.srcDir("../../app/src/main/java/ru/tsvetaeva/daily/core") } }
application { mainClass.set("ProbeKt") }
kotlin { jvmToolchain(17) }
