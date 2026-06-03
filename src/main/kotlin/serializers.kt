import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
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

    private val mavenLocalRepo: Path by lazy {
        val repo = config.outputDirectory.resolve("repo")
        Files.createDirectories(repo)
        repo
    }

    private val Project.projectDir: Path get() = config.outputDirectory.resolve(name)

    fun serializeProjects(projects: List<Project>) {
        Files.createDirectories(config.outputDirectory)

        for (project in projects) {
            generateProject(project)
        }

        when (config.generationMode) {
            Config.GenerationMode.SINGLE_GRADLE_PROJECT -> {
                copyGradleWrapperTo(config.outputDirectory)
                writeGradlePropertiesFile(
                    config.outputDirectory,
                    logCompilerArgs = true,
                    useConfigurationCache = true,
                )
                writeBuildSettingsFile(config.outputDirectory, rootProjectName = "kotlin-klib-benchmarks", includedProjects = projects)
                writeBuildFile(
                    dir = config.outputDirectory,
                    specifyPluginVersion = true,
                    dontApplyPlugin = true,
                    publishing = false,
                    useMavenLocalRepo = false,
                    project = null,
                )
                writeBuildShFile(
                    dir = config.outputDirectory,
                    fileName = "build-all.sh",
                    tasks = listOf("./gradlew assemble")
                )
            }

            Config.GenerationMode.SEPARATE_GRADLE_PROJECTS -> {
                writeBuildShFile(
                    dir = config.outputDirectory,
                    fileName = "build-libs.sh",
                    tasks = projects.filter { !it.isApplication }.flatMap { project ->
                        listOf(
                            "echo About to build library: ${project.name}",
                            "${project.projectDir.resolve("gradlew")} -p ${project.projectDir} publish -q"
                        )
                    }
                )

                writeBuildShFile(
                    dir = config.outputDirectory,
                    fileName = "build-app.sh",
                    tasks = projects.filter { it.isApplication }.map { project ->
                        "${project.projectDir.resolve("gradlew")} -p ${project.projectDir} assemble"
                    }

                )
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyGradleWrapperTo(dir: Path) {
        basePath.resolve("gradlew").copyTo(dir.resolve("gradlew"))
        basePath.resolve("gradle").copyToRecursively(dir.resolve("gradle"), followLinks = true, overwrite = false)
    }

    private fun writeBuildSettingsFile(dir: Path, rootProjectName: String, includedProjects: List<Project>) {
        dir.resolve("settings.gradle.kts").writeText(
            buildString {
                appendLine("rootProject.name = \"$rootProjectName\"")
                appendLine()
                includedProjects.chunked(10).forEach { chunk ->
                    chunk.joinTo(this, prefix = "include(", postfix = ")\n") { '"' + it.name + '"' }
                }
            }
        )
    }

    private fun writeBuildFile(
        dir: Path,
        specifyPluginVersion: Boolean,
        dontApplyPlugin: Boolean,
        publishing: Boolean,
        useMavenLocalRepo: Boolean,
        project: Project?,
    ) {
        val pluginVersion = if (specifyPluginVersion) " version \"${config.kotlinVersion}\"" else ""
        val applyPlugin = if (dontApplyPlugin) " apply false" else ""

        dir.resolve("build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    kotlin(\"multiplatform\")$pluginVersion$applyPlugin")
                if (publishing) appendLine("    id(\"maven-publish\")")
                appendLine("}")
                appendLine()
                appendLine("repositories {")
                appendLine("    mavenCentral()")
                if (useMavenLocalRepo) appendLine("    maven(\"$mavenLocalRepo\")")
                appendLine("}")
                appendLine()
                if (config.generationMode.useSeparateGradleProjects && project != null) {
                    appendLine("group = \"${project.mavenGroup}\"")
                    appendLine("version = \"${project.mavenVersion}\"")
                    appendLine()
                }
                if (project != null) {
                    fun target(name: String): String = name + when (project.kind) {
                        Project.Kind.REGULAR -> "()"
                        Project.Kind.APP -> " { binaries.executable { entryPoint = \"main\" } }"
                        Project.Kind.CINTEROP -> "{ compilations[\"main\"].cinterops { val nativeLib by creating {} } }"
                    }

                    fun dependency(otherProject: Project): String =
                        if (config.generationMode.useSeparateGradleProjects)
                            "\"${otherProject.gav}\""
                        else
                            "project(\":${otherProject.name}\")"

                    appendLine("kotlin {")
                    for (target in TESTED_TARGETS) {
                        appendLine("    ${target(target)}")
                    }
                    appendLine()
                    appendLine("    sourceSets {")
                    appendLine("        nativeMain.dependencies {")
                    project.dependencies.joinTo(this, "") {
                        "            implementation(${dependency(it)})\n"
                    }
                    appendLine("        }")
                    appendLine("    }")
                    appendLine("}")
                    appendLine()
                    if (project.isApplication) {
                        appendLine("// Force all tasks to be never UP-TO-DATE.")
                        appendLine("tasks.all { outputs.upToDateWhen { false } }")
                        appendLine()
                    }

                }
                if (publishing && useMavenLocalRepo) {
                    appendLine(
                        """
                        |publishing {
                        |    repositories {
                        |        maven("$mavenLocalRepo")
                        |    }
                        |}
                        """.trimMargin()
                    )
                }
            }
        )
    }

    private fun writeGradlePropertiesFile(
        dir: Path,
        logCompilerArgs: Boolean,
        useConfigurationCache: Boolean,
    ) {
        dir.resolve("gradle.properties").writeText(
            listOfNotNull(
                "kotlin.internal.compiler.arguments.log.level=warning".takeIf { logCompilerArgs },
                "kotlin.mpp.enableCInteropCommonization.nowarn=true",
                "org.gradle.configuration-cache=true".takeIf { useConfigurationCache },
                "org.gradle.jvmargs=-Xmx16g",
            ).joinToString("\n", postfix = "\n")
        )
    }

    private fun writeBuildShFile(dir: Path, fileName: String, tasks: List<String>) {
        val shFile = dir.resolve(fileName)
        shFile.writeText(
            buildString {
                appendLine("#!/bin/sh")
                appendLine()
                appendLine("set -e")
                appendLine()
                for (task in tasks) {
                    appendLine(task)
                }
            }
        )
        Files.setPosixFilePermissions(
            shFile,
            setOf(
                OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                GROUP_READ, GROUP_EXECUTE,
                OTHERS_READ, OTHERS_EXECUTE,
            )
        )
    }

    private fun generateProject(project: Project) {
        Files.createDirectories(project.projectDir)

        if (config.generationMode.useSeparateGradleProjects) {
            copyGradleWrapperTo(project.projectDir)
            writeGradlePropertiesFile(
                dir = project.projectDir,
                logCompilerArgs = project.isApplication,
                useConfigurationCache = project.isApplication,
            )
            writeBuildSettingsFile(project.projectDir, rootProjectName = project.name, includedProjects = emptyList())
        }

        writeBuildFile(
            dir = project.projectDir,
            specifyPluginVersion = config.generationMode.useSeparateGradleProjects,
            dontApplyPlugin = false,
            publishing = config.generationMode.useSeparateGradleProjects && !project.isApplication,
            useMavenLocalRepo = config.generationMode.useSeparateGradleProjects,
            project = project,
        )

        generateSourceFiles(project)
    }

    private fun generateSourceFiles(project: Project) {
        fun writeKotlinSourceFile(specificTarget: String) {
            val sourceSetName = "${specificTarget}Main"
            val kotlinSrcDir = project.projectDir.resolve("src/$sourceSetName/kotlin")
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
            val defSrcDir = project.projectDir.resolve("src/nativeInterop/cinterop")
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
