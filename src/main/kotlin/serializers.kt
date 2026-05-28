import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.name
import kotlin.io.path.toPath
import kotlin.io.path.writeText

internal class ProjectSerializer(private val config: Config) {
    private val basePath: Path by lazy {
        val currentClassPath = this::class.java.protectionDomain.codeSource.location.toURI().toPath()
        generateSequence(currentClassPath) { it.parent }.first { it.name == "build" }.parent
    }

    fun serializeProjects(projects: List<Project>) {
        Files.createDirectories(config.outputDirectory)

        copyGradleWrapper()
        writeMainBuildSettings(projects)
        writeMainBuildFile()
        writeGradlePropertiesFile()

        for (project in projects) {
            generateProject(project)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyGradleWrapper() {
        basePath.resolve("gradlew").copyTo(config.outputDirectory.resolve("gradlew"))
        basePath.resolve("gradle").copyToRecursively(config.outputDirectory.resolve("gradle"), followLinks = true, overwrite = false)
    }

    private fun writeMainBuildSettings(projects: List<Project>) {
        config.outputDirectory.resolve("settings.gradle.kts").writeText(
            buildString {
                appendLine("rootProject.name = \"kotlin-klib-benchmarks\"")
                appendLine()
                projects.chunked(10).forEach { chunk ->
                    chunk.joinTo(this, prefix = "include(", postfix = ")\n") { '"' + it.name + '"' }
                }
            }
        )
    }

    private fun writeMainBuildFile() {
        config.outputDirectory.resolve("build.gradle.kts").writeText(
            """
            |plugins {
            |    kotlin("multiplatform") version "${config.kotlinVersion}" apply false
            |}
            |
            |allprojects {
            |    repositories {
            |        mavenCentral()
            |    }
            |
            |    // Force all tasks to be never UP-TO-DATE.
            |    tasks.all { outputs.upToDateWhen { false } }
            |}
            """.trimMargin()
        )
    }

    private fun writeGradlePropertiesFile() {
        config.outputDirectory.resolve("gradle.properties").writeText(
            listOf(
                "kotlin.internal.compiler.arguments.log.level=warning",
                "org.gradle.jvmargs=-Xmx16g",
            ).joinToString("\n", postfix = "\n")
        )
    }

    private fun generateProject(project: Project) {
        val projectDir = config.outputDirectory.resolve(project.name)
        Files.createDirectories(projectDir)

        fun target(name: String): String = name + when (project.kind) {
            Project.Kind.REGULAR -> "()"
            Project.Kind.APP -> " { binaries.executable { entryPoint = \"main\" } }"
            Project.Kind.CINTEROP -> "{ compilations[\"main\"].cinterops { val nativeLib by creating {} } }"
        }

        projectDir.resolve("build.gradle.kts").writeText(
            buildString {
                appendLine(
                    """
                    |plugins {
                    |    kotlin("multiplatform")
                    |}
                    |
                    |kotlin {
                    """.trimMargin()
                )
                for (target in TESTED_TARGETS) {
                    appendLine("    ${target(target)}")
                }
                appendLine()
                appendLine(
                    """
                    |    sourceSets {
                    |        nativeMain.dependencies {
                    """.trimMargin()
                )
                project.dependencies.joinTo(this, "") {
                    "            implementation(project(\":${it.name}\"))\n"
                }
                appendLine(
                    """
                    |        }
                    |    }
                    |}
                    """.trimMargin()
                )
            }
        )

        generateSourceFiles(projectDir, project)
    }

    private fun generateSourceFiles(projectDir: Path, project: Project) {
        fun writeKotlinSourceFile(specificTarget: String) {
            val sourceSetName = "${specificTarget}Main"
            val kotlinSrcDir = projectDir.resolve("src/$sourceSetName/kotlin")
            Files.createDirectories(kotlinSrcDir)

            val kotlinSourceFileName = project.name.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }.replace("_", "") + ".kt"
            kotlinSrcDir.resolve(kotlinSourceFileName).writeText(
                buildString {
                    appendLine("@file:Suppress(\"unused\", \"PropertyName\", \"FunctionName\", \"ClassName\", \"RemoveRedundantQualifierName\", \"UnusedImport\", \"UnusedVariable\")")
                    if (project.isCInterop) appendLine("@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)")
                    appendLine()

                    if (project.packageName.isNotEmpty()) {
                        appendLine("package ${project.packageName}")
                        appendLine()
                    }

                    // a placeholder for imports
                    appendLine(IMPORTS)
                    appendLine()

                    if (!project.isCInterop) {
                        val initialIndent = Indent()
                        project.declarations.forEach { declaration -> render(declaration, initialIndent) }
                        appendLine()
                    }

                    renderProjectMainFunction(project)
                }
            )
        }

        // we need to put Kotlin source code into each leaf source set,
        // otherwise the resolve from the Kotlin code into C-interop declarations won't work
        for (target in TESTED_TARGETS) {
            writeKotlinSourceFile(target)
        }

        if (project.isCInterop) {
            val defSrcDir = projectDir.resolve("src/nativeInterop/cinterop")
            Files.createDirectories(defSrcDir)

            defSrcDir.resolve("nativeLib.def").writeText(
                buildString {
                    appendLine("package=${project.packageName}")
                    appendLine("language=C")
                    appendLine("---")
                    project.declarations.forEach { declaration ->
                        check(declaration is Declaration.Function)
                        // ignore types and return values for now
                        appendLine("int ${declaration.name}() { return 100500; }")
                    }
                }
            )
        }
    }

    private fun StringBuilder.render(declaration: Declaration, indent: Indent) {
        when (declaration) {
            is Declaration.Property -> render(property = declaration, indent)
            is Declaration.Function -> render(function = declaration, indent)
            is Declaration.Class -> render(clazz = declaration, indent)
        }
    }

    private fun StringBuilder.render(property: Declaration.Property, indent: Indent) {
        append(indent).appendLine("var ${property.name}: ${property.type} = ${property.defaultValue}")
    }

    private fun StringBuilder.render(function: Declaration.Function, indent: Indent) {
        append(indent).appendLine("fun ${function.name}(): ${function.returnType} = ${function.returnValue}")
    }

    private fun StringBuilder.render(clazz: Declaration.Class, indent: Indent) {
        append(indent).appendLine("class ${clazz.name} {")
        val nextIndent = indent.next()
        clazz.declarations.forEach { declaration -> render(declaration, nextIndent) }
        append(indent).appendLine("}")
    }

    private fun StringBuilder.renderVariable(variableName: String, initializer: Declaration, indent: Indent) {
        append(indent).append("val $variableName = ").appendLine(getSimpleNameForCall(initializer))
    }

    private fun getFqName(project: Project, declaration: Declaration): String {
        val prefix = if (project.packageName.isNotEmpty()) "${project.packageName}." else ""
        return prefix + declaration.name
    }

    private fun getSimpleNameForCall(declaration: Declaration): String {
        return if (declaration !is Declaration.Property) "${declaration.name}()" else declaration.name
    }

    /**
     * The projects main function: `<project_name>_main(): Unit`.
     *
     * This function serves for two purposes:
     * 1. It accesses every declaration declared in the current project.
     * 2. It calls main functions from dependency projects.
     */
    private fun StringBuilder.renderProjectMainFunction(project: Project) {
        var tempVariableIndex = 0
        val imports = mutableListOf<String>()

        fun mainFunctionName(project: Project) = if (project.isApplication) "main" else "${project.name}_main"
        fun nextTempVariableName() = "tmp${tempVariableIndex++}"

        appendLine("fun ${mainFunctionName(project)}() {")
        val indent = Indent().next()
        append(indent).appendLine("/* own declarations */")

        project.declarations.forEach { declaration ->
            renderVariable(variableName = nextTempVariableName(), initializer = declaration, indent)
        }

        appendLine()
        append(indent).appendLine("/* dependencies */")
        project.dependencies.forEach { dependency ->
            val fakeMainFunction = Declaration.Function(mainFunctionName(dependency), "", "")
            imports += getFqName(dependency, fakeMainFunction)

            renderVariable(variableName = nextTempVariableName(), initializer = fakeMainFunction, indent)
        }

        appendLine()
        append(indent).appendLine("/* custom */")

        if (project.isApplication) {
            append(indent).appendLine("println(\"Successfully executed application for $config\")")
        }

        appendLine("}")

        if (imports.isNotEmpty()) {
            val importsPlaceholderIndex = indexOf(IMPORTS)
            check(importsPlaceholderIndex > 0)

            insert(importsPlaceholderIndex + IMPORTS.length + 1, imports.joinToString(separator = "\n", postfix = "\n") { "import $it" })
        }
    }

    private class Indent(private val indent: String = "") {
        fun next() = Indent("$indent    ")

        override fun toString() = indent
    }

    companion object {
        private val TESTED_TARGETS = listOf("macosArm64", "iosArm64")
        private const val IMPORTS = "// IMPORTS"
    }
}
