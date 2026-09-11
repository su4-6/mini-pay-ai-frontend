plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(kotlin("test"))
    // ⚠️ kotlin("test") 只带 junit-jupiter-API，不带 ENGINE。
    // 缺引擎时 JUnit Platform 无法执行任何用例，Gradle 会报成误导性的
    // "ClassNotFoundException: <测试类>"，看起来像编译/类路径问题，实际是用例根本没跑。
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // ⚠️ 必须显式声明测试类目录。
    // 本模块（纯 Kotlin JVM，Android 工程内）里 Kotlin 插件的 kotlin/test 输出
    // 没有被登记进 testClassesDirs，于是 JUnit Platform 在 worker 里
    // java.lang.ClassNotFoundException: com.minipay.bridge.FoodBridgePolicyTest，
    // 用例一个都不执行，却每次都报 BUILD FAILED —— 等于没有回归覆盖。
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    testClassesDirs = files(
        layout.buildDirectory.dir("classes/kotlin/test"),
        layout.buildDirectory.dir("classes/java/test"),
    )
}
