import Parameter.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

internal data class Config(
    val kotlinVersion: String,
    val outputDirectory: Path,
    val numberOfProjects: Int,
    val cInteropProjects: Int,
    val declarationsPerProject: Int,
    val dependenciesPerProject: Int,
    val uniquePackages: Int,
)

internal fun parseArgs(args: Array<String>): Config {
    val map = mutableMapOf<Parameter, String>()
    var i = 0
    while (i < args.size) {
        val parameter = Parameter.getByAlias(args[i])
        if (i + 1 >= args.size) error("Missing value for $parameter")
        map[parameter] = args[i + 1]
        i += 2
    }

    if (map.isEmpty() || HELP in map) printHelpAndExit()

    fun getRequiredArgument(parameter: Parameter): String = map[parameter] ?: error("Missing required parameter $parameter")

    fun getRequiredIntArgument(parameter: Parameter, minValue: Int, maxValue: Int): Int {
        val rawValue = getRequiredArgument(parameter)
        val value = rawValue.toIntOrNull() ?: printErrorAndExit("Cannot parse value for $parameter: $rawValue")
        if (value < minValue) printErrorAndExit("The value for $parameter cannot be less than $minValue")
        if (value > maxValue) printErrorAndExit("The value for $parameter cannot be greater than $maxValue")
        return value
    }

    val outputDirectory = Paths.get(getRequiredArgument(OUTPUT_DIRECTORY))
    if (Files.isDirectory(outputDirectory)) {
        if (Files.newDirectoryStream(outputDirectory).any())
            printErrorAndExit("The output directory $outputDirectory is non-empty. Please remove the directory contents.")
    } else if (Files.exists(outputDirectory)) {
        printErrorAndExit("The $outputDirectory path is not a directory.")
    }

    val numberOfProjects = getRequiredIntArgument(NUMBER_OF_PROJECTS, minValue = 1, maxValue = 100_000)

    return Config(
        kotlinVersion = getRequiredArgument(KOTLIN_VERSION),
        outputDirectory = outputDirectory,
        numberOfProjects = numberOfProjects,
        cInteropProjects = getRequiredIntArgument(CINTEROP_PROJECTS, minValue = 0, maxValue = numberOfProjects),
        declarationsPerProject = getRequiredIntArgument(DECLARATIONS_PER_PROJECT, minValue = 1, maxValue = 100_000),
        dependenciesPerProject = getRequiredIntArgument(DEPENDENCIES_PER_PROJECT, minValue = 0, maxValue = numberOfProjects),
        uniquePackages = getRequiredIntArgument(UNIQUE_PACKAGES, minValue = 1, maxValue = numberOfProjects),
    )
}

private enum class Parameter(val alias: String, val description: String) {
    HELP(alias = "--help", description = "Print help"),

    /** General settings */
    KOTLIN_VERSION(alias = "--kotlin-version", description = "Kotlin version"),
    OUTPUT_DIRECTORY(alias = "--output-dir", description = "Path to the output directory (must be empty)"),
    NUMBER_OF_PROJECTS(alias = "--number-of-projects", description = "Number of projects (positive number)"),
    CINTEROP_PROJECTS(alias = "--cinterop-projects", description = "Number of projects (non-negative number)"),

    /** Project settings */
    DECLARATIONS_PER_PROJECT(alias = "--declarations-per-project", description = "Declarations per projects (positive number)"),
    DEPENDENCIES_PER_PROJECT(alias = "--dependencies-per-project", description = "Number of dependencies per project (non-negative number)"),
    UNIQUE_PACKAGES(alias = "--unique-packages", description = "Unique packages per all projects (positive number)"),

    ;

    override fun toString() = "'$alias'"

    companion object {
        fun getByAlias(alias: String): Parameter {
            entries.firstOrNull { it.alias == alias }?.let { return it }
            printErrorAndExit("Unknown argument '$alias'")
        }
    }
}

private fun printErrorAndExit(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun printHelpAndExit(): Nothing {
    println("Usage:")
    entries.forEach { entry ->
        println(
            buildString {
                append("  ${entry.alias}")
                while (length != 30) append(" ")
                append(entry.description)
            }
        )
    }
    exitProcess(0)
}
