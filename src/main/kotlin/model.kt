internal class Project(
    val name: String,
    val packageName: String,
    val declarations: List<Declaration>,
    val dependencies: List<Project>,
    val isApplication: Boolean,
) {
    override fun toString() =
        "Project[$name, package=$packageName, declarations=${declarations.size}, dependencies=${dependencies.size}"
}

sealed class Declaration(val name: String) {
    class Class(name: String, val declarations: List<Declaration>) : Declaration(name)
    class Function(name: String, val returnType: String, val returnValue: String) : Declaration(name)
    class Property(name: String, val type: String, val defaultValue: String) : Declaration(name)
}
