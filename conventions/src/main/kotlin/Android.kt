import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.PluginManager
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import wing.AndroidCommonExtension
import wing.AndroidComponentsExtensions
import wing.isAndroidLibrary
import wing.log
import wing.purple
import wing.vWings

interface Android {

    /**
     * ```kotlin
     *     override fun pluginConfigs(): PluginManager.() -> Unit = {
     *         //有需要的话执行父类逻辑
     *         super.pluginConfigs().invoke(this)
     *         //执行自己的逻辑
     *         apply("kotlin-android")
     *     }
     * ```
     */
    fun pluginConfigs(): PluginManager.(Project) -> Unit

    /**
     * ```kotlin
     *     override fun androidExtensionConfig(): AndroidExtension.(Project, VersionCatalog) -> Unit {
     *         return { project, versionCatalog ->
     *             //有需要的话执行父类逻辑
     *             super.androidExtensionConfig().invoke(this,project,versionCatalog)
     *             //自己特有的逻辑
     *         }
     *     }
     * ```
     */
    fun androidExtensionConfig(): AndroidCommonExtension.(Project, VersionCatalog) -> Unit

    fun androidComponentsExtensionConfig(): AndroidComponentsExtensions.(Project, VersionCatalog) -> Unit

    fun kotlinOptionsConfig(): KotlinCommonCompilerOptions.(Project) -> Unit

    /**
     * ```kotlin
     *     override fun dependenciesConfig(): DependencyHandlerScope.(VersionCatalog) -> Unit = { vlibs: VersionCatalog ->
     *         //有需要的话执行父类逻辑
     *         super.dependenciesConfig().invoke(this, vlibs)
     *         //自己特有的逻辑
     *     }
     * ```
     */
    fun dependenciesConfig(): DependencyHandlerScope.(Project, VersionCatalog) -> Unit

}

open class BaseAndroid(val android: Android? = null) : Android {

    /**
     * ```kotlin
     *     override fun pluginConfigs(): PluginManager.() -> Unit = {
     *         //有需要的话执行父类逻辑
     *         super.pluginConfigs().invoke(this)
     *         //执行自己的逻辑
     *         apply("kotlin-android")
     *     }
     * ```
     */
    override fun pluginConfigs(): PluginManager.(Project) -> Unit = {
        it.log("pluginConfigs()  ${this@BaseAndroid}".purple)
        android?.pluginConfigs()?.invoke(this, it)
    }

    /**
     * ```kotlin
     *     override fun androidExtensionConfig(): AndroidExtension.(Project, VersionCatalog) -> Unit {
     *         return { project, versionCatalog ->
     *             //有需要的话执行父类逻辑
     *             super.androidExtensionConfig().invoke(this,project,versionCatalog)
     *             //自己特有的逻辑
     *         }
     *     }
     * ```
     */
    override fun androidExtensionConfig(): AndroidCommonExtension.(Project, VersionCatalog) -> Unit = { project, versionCatalog ->
        project.log("androidExtensionConfig()  ${this@BaseAndroid}".purple)
        //有需要的话执行父类逻辑
        android?.androidExtensionConfig()?.invoke(this, project, versionCatalog)
    }


    override fun androidComponentsExtensionConfig(): AndroidComponentsExtensions.(Project, VersionCatalog) -> Unit =
        { project, versionCatalog ->
            project.log("androidComponentsExtensionConfig()  ${this@BaseAndroid}".purple)
            android?.androidComponentsExtensionConfig()?.invoke(this, project, versionCatalog)
        }

    override fun kotlinOptionsConfig(): KotlinCommonCompilerOptions.(Project) -> Unit = { project ->
        project.logger.log(LogLevel.DEBUG, "kotlinOptionsConfig()  ${this@BaseAndroid}".purple)
        android?.kotlinOptionsConfig()?.invoke(this, project)
    }

    /**
     * ```kotlin
     *     override fun dependenciesConfig(): DependencyHandlerScope.(VersionCatalog) -> Unit = { vlibs: VersionCatalog ->
     *         //有需要的话执行父类逻辑
     *         super.dependenciesConfig().invoke(this, vlibs)
     *         //自己特有的逻辑
     *     }
     * ```
     */
    override fun dependenciesConfig(): DependencyHandlerScope.(Project, VersionCatalog) -> Unit = { project, versionCatalog ->
        project.log("dependenciesConfig()  ${this@BaseAndroid}".purple)
        android?.dependenciesConfig()?.invoke(this, project, versionCatalog)
    }
}

class AndroidBase(pre: Android? = null) : BaseAndroid(pre) {

    override fun pluginConfigs(): PluginManager.(Project) -> Unit = {
        super.pluginConfigs().invoke(this, it)
        //<editor-fold desc="android project default plugin">
        //如果根build.gradle没在plugins中apply的话这里无法依赖，之后补充自动依赖
        apply("kotlin-android")
        //apply("org.jetbrains.kotlin.android")
        apply("kotlin-parcelize")
        //</editor-fold>
    }

    override fun androidExtensionConfig(): AndroidCommonExtension.(Project, VersionCatalog) -> Unit = { project, catalog ->
        super.androidExtensionConfig().invoke(this, project, catalog)
        //<editor-fold desc="android project default config">
        compileSdk = catalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
        defaultConfig {
            minSdk = catalog.findVersion("android-minSdk").get().requiredVersion.toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables {
                useSupportLibrary = true
            }
        }
        buildFeatures {
            buildConfig = true
        }
        compileOptions {
            // Up to Java 11 APIs are available through desugaring
            // https://developer.android.com/studio/write/java11-minimal-support-table
            sourceCompatibility = JavaVersion.VERSION_18
            targetCompatibility = JavaVersion.VERSION_18
            encoding = "UTF-8"
            //isCoreLibraryDesugaringEnabled = true
        }
        //</editor-fold>
    }

    override fun kotlinOptionsConfig(): KotlinCommonCompilerOptions.(Project) -> Unit = { project ->
        super.kotlinOptionsConfig().invoke(this, project)
        freeCompilerArgs.add("-Xcontext-receivers")
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }

    override fun dependenciesConfig(): DependencyHandlerScope.(Project, VersionCatalog) -> Unit = { project, catalog ->
        super.dependenciesConfig().invoke(this, project, catalog)
        val androidLibrary = project.isAndroidLibrary
        //library默认不添加依赖,除非配置了config.android.dependencies.force=true
        if (!androidLibrary or project.hasProperty("config.android.dependencies.force")) {
            //<editor-fold desc="android project default dependencies">
            catalog.findLibrary("koin-bom").ifPresent { koinBom ->
                catalog.findBundle("koin").ifPresent {
                    project.log("implementation(koin)")
                    add("implementation", platform(koinBom))
                    add("implementation", it)
                }
            }

            catalog.findLibrary("okhttp-bom").ifPresent { okhttpBom ->
                catalog.findBundle("okhttp").ifPresent {
                    project.log("implementation(okhttp-bom)")
                    add("implementation", platform(okhttpBom))
                    add("implementation", it)
                }
            }

            catalog.findBundle("android-project").ifPresentOrElse({ androidProject ->
                project.log("implementation(android-project)")
                add("implementation", androidProject)
            }) {
                if (!androidLibrary) {
                    project.log("implementation(androidx...appcompat)")
                    add("implementation", catalog.findLibrary("androidx-navigation-ui-ktx").get())
                    add("implementation", catalog.findLibrary("androidx-navigation-fragment-ktx").get())
                    add("implementation", catalog.findLibrary("lifecycle-livedata-ktx").get())
                    add("implementation", catalog.findLibrary("lifecycle-viewmodel-ktx").get())
                    add("implementation", catalog.findLibrary("google-material").get())
                    add("implementation", catalog.findLibrary("androidx-appcompat").get())
                    add("implementation", catalog.findLibrary("androidx-core-ktx").get())
                    add("implementation", catalog.findLibrary("androidx-constraintlayout").get())
                }
            }
            //catalog.findBundle("android-view").ifPresent { views ->
            //    log("implementation(android-view)")
            //    add("implementation", views)
            //}
            catalog.findBundle("ktor").ifPresent { ktor ->
                project.log("implementation(ktor)")
                add("implementation", ktor)
            }
            catalog.findLibrary("test-junit").ifPresent { jUnit ->
                add("testImplementation", jUnit)
            }
            catalog.findBundle("androidx-benchmark").ifPresent { androidxBenchmark ->
                //包括 androidx-test-ext-junit , androidx-test-espresso-core
                add("androidTestImplementation", androidxBenchmark)
            }
            project.vWings?.findBundle("sparkj")?.ifPresent { sparkj ->
                project.log("implementation(sparkj)")
                add("implementation", sparkj)
            }
            //</editor-fold>
        }
    }
}

class AndroidRoom(pre: Android? = null) : BaseAndroid(pre) {
    private fun Project.ksp(config: KspExtension.() -> Unit) = extensions.getByType<KspExtension>().config()
    private fun Project.room(config: RoomExtension.() -> Unit) = extensions.getByType<RoomExtension>().config()
    override fun pluginConfigs(): PluginManager.(Project) -> Unit = {
        super.pluginConfigs().invoke(this, it)
        apply("androidx.room")
        apply("com.google.devtools.ksp")

        with(it) {
            //https://kotlinlang.org/docs/ksp-quickstart.html#create-a-processor-of-your-own
            ksp {
                //room 配置 生成 Kotlin 源文件，而非 Java 代码。需要 KSP。默认值为 false。 有关详情，请参阅版本 2.6.0 的说明
                arg("room.generateKotlin", "true")
            }

            //room 指南
            //对于非 Android 库（即仅支持 Java 或 Kotlin 的 Gradle 模块），您可以依赖 androidx.room:room-common 来使用 Room 注解
            //https://developer.android.google.cn/training/data-storage/room?hl=zh-cn
            room {
                //使用 Room Gradle 插件时需要设置 schemaDirectory。这会配置 Room 编译器以及各种编译任务及其后端（javac、KAPT、KSP），
                //以将架构文件输出到变种文件夹（例如 schemas/flavorOneDebug/com.package.MyDatabase/1.json）中。这些文件应签入代码库中，以用于验证和自动迁移。
                schemaDirectory("$projectDir/schemas")
            }
        }
    }

    override fun dependenciesConfig(): DependencyHandlerScope.(Project, VersionCatalog) -> Unit =
        { project, catalog ->
            super.dependenciesConfig().invoke(this, project, catalog)
            catalog.findBundle("androidx-room").ifPresent {
                add("implementation", it)
            }
            catalog.findLibrary("androidx-room-compiler").ifPresent {
                add("annotationProcessor", it)
                // To use Kotlin Symbol Processing (KSP)
                add("ksp", it)
            }
        }
}