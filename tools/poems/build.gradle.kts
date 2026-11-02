// Сборщик встроенных стихов: тем же кодом, что в приложении, обходит Викитеку
// и пишет app/src/main/assets/poems/<автор>.json.gz. Запуск — см. README («Встроенные стихи»).
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

sourceSets {
    main {
        kotlin {
            // Ядро приложения (разбор Викитеки, обход, склейка изданий) и клиент API — без Android.
            srcDir("../../app/src/main/java")
            include(
                "tools/**",
                "ru/tsvetaeva/daily/core/**",
                "ru/tsvetaeva/daily/data/WikiApi.kt",
                "ru/tsvetaeva/daily/data/PoemJson.kt",
            )
        }
    }
}

application { mainClass.set("tools.MainKt") }
