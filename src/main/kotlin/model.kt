internal class Project(
    val name: String,
    val packageName: String,
    val declarations: List<Declaration>,
    val dependencies: List<Project>,
    val kind: Kind,
) {
    enum class Kind { REGULAR, APP, CINTEROP }

    val isApplication get() = kind == Kind.APP
    val isCInterop get() = kind == Kind.CINTEROP

    val mavenGroup get() = "org.sample.kotlin.generated.klib"
    val mavenVersion get() = "0.0.1"

    val ga get() = "$mavenGroup:$name"
    val gav get() = "$ga:$mavenVersion"

    init {
        if (isApplication) {
            check(packageName.isEmpty())
            check(declarations.isEmpty())
            check(dependencies.isNotEmpty())
        } else if (isCInterop) {
            check(declarations.isNotEmpty())
            check(declarations.all { it is Declaration.Function }) // only functions supported for now
        } else {
            check(declarations.isNotEmpty())
        }
    }

    override fun toString() =
        "Project[$name, $kind, package=$packageName, declarations=${declarations.size}, dependencies=${dependencies.size}"
}

sealed class Declaration(val name: String) {
    class Class(name: String, val declarations: List<Declaration>) : Declaration(name)
    class Function(name: String, val returnType: String, val returnValue: String) : Declaration(name)
    class Property(name: String, val type: String, val defaultValue: String) : Declaration(name)
}
