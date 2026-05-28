internal class ProjectGenerator(private val config: Config) {
    private val projectNameGenerator = ProjectNameGenerator(config)
    private val packageNameGenerator = PackageNameGenerator(config)
    private val declarationGenerator = DeclarationGenerator(config)

    private val generatedProjects = ArrayList<Project>()

    fun generateProjects(): List<Project> {
        val cInteropProjectIndices = generateSequence(0) { it + (config.numberOfProjects / config.cInteropProjects) }
            .take(config.cInteropProjects)
            .toSet()

        (0 until config.numberOfProjects).forEach { index ->
            generateProject(isCInterop = index in cInteropProjectIndices)
        }
        check(generatedProjects.size == config.numberOfProjects)
        check(generatedProjects.none { it.isApplication })

        generateProject(isApplication = true)
        check(generatedProjects.count { it.isApplication } == 1)

        return generatedProjects
    }

    private fun generateProject(isApplication: Boolean = false, isCInterop: Boolean = false) {
        check(!isApplication || !isCInterop)

        val dependencies = generatedProjects.takeLast(config.dependenciesPerProject)

        val project = if (isApplication) {
            Project(
                name = "app",
                packageName = "",
                declarations = emptyList(),
                dependencies = dependencies,
                kind = Project.Kind.APP,
            )
        } else {
            val projectIndex = generatedProjects.size
            Project(
                name = projectNameGenerator.getProjectName(projectIndex),
                packageName = packageNameGenerator.getPackageName(projectIndex),
                declarations = declarationGenerator.getDeclarations(onlyTopLevelFunctions = isCInterop),
                dependencies = dependencies,
                kind = if (isCInterop) Project.Kind.CINTEROP else Project.Kind.REGULAR,
            )
        }

        generatedProjects += project
    }
}

private class ProjectNameGenerator(private val config: Config) {
    fun getProjectName(projectIndex: Int): String {
        return "project_" + projectIndex.padWithZeros(config.numberOfProjects)
    }
}

private class PackageNameGenerator(private val config: Config) {
    fun getPackageName(projectIndex: Int): String {
        val avgProjectsWithSamePackageName: Double = config.numberOfProjects.toDouble() / config.uniquePackages
        val packageNameIndex: Int = (projectIndex / avgProjectsWithSamePackageName).toInt()

        //  There can be 0, 1, 2 or 3 package segments depending on the package index.
        val nameSegmentsInPackageName = packageNameIndex % 4

        return if (nameSegmentsInPackageName == 0)
            "" // empty package
        else {
            buildList {
                this += "package_" + packageNameIndex.padWithZeros(config.numberOfProjects)
                if (nameSegmentsInPackageName > 1) {
                    this += "foo"
                    if (nameSegmentsInPackageName > 2) {
                        this += "bar"
                    }
                }
            }.joinToString(".")
        }
    }
}

private class DeclarationGenerator(private val config: Config) {
    private var declarationSerialNumber = 0

    fun getDeclarations(onlyTopLevelFunctions: Boolean) = buildList {
        repeat(config.declarationsPerProject) {
            generateCallables(onlyTopLevelFunctions)
            if (!onlyTopLevelFunctions) {
                generateClass {
                    generateClass {
                        generateClass {
                            generateCallables()
                        }
                        generateCallables()
                    }
                    generateCallables()
                }
            }
        }
    }

    private inline fun newProperty(type: String, defaultValue: (id: Int) -> String): Declaration.Property {
        val id = declarationSerialNumber++
        return Declaration.Property(
            name = "property_$id",
            type = type,
            defaultValue = defaultValue(id),
        )
    }

    private inline fun newFunction(type: String, returnValue: (id: Int) -> String): Declaration.Function {
        val id = declarationSerialNumber++
        return Declaration.Function(
            name = "function_$id",
            returnType = type,
            returnValue = returnValue(id),
        )
    }

    private inline fun newClass(members: () -> List<Declaration>): Declaration.Class {
        val id = declarationSerialNumber++
        return Declaration.Class(
            name = "Class_$id",
            declarations = members(),
        )
    }

    private fun MutableCollection<Declaration>.generateCallables(onlyTopLevelFunctions: Boolean = false) {
        if (!onlyTopLevelFunctions) {
            this += newProperty("kotlin.Int") { id -> id.toString() }
            this += newProperty("kotlin.String") { id -> "\"property_$id\"" }
        }

        this += newFunction("kotlin.collections.List<kotlin.Double>") { id -> "listOf($id * 3.14)" }
        this += newFunction("kotlin.Byte") { id -> "$id.toByte()" }
    }

    private inline fun MutableCollection<Declaration>.generateClass(generateMembers: MutableCollection<Declaration>.() -> Unit) {
        this += newClass { buildList(generateMembers) }
    }
}

private fun Int.padWithZeros(maxValueExclusive: Int) =
    toString().padStart((maxValueExclusive - 1).toString().length, '0')
