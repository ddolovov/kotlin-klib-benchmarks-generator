fun main(args: Array<String>) {
    val config = parseArgs(args)
    val projects = ProjectGenerator(config).generateProjects()
    println()
}
