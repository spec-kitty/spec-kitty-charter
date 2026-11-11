/**
 * Mutation testing stays out of the required check (ADR-0060, #387): PIT and its floor check run only in the nightly
 * (`mutation-nightly.yml`), never as a dependency of `verifyAll` or of a module's `check`. The root build applies
 * [reached] to the task graph of every invocation and fails it when a gate reaches a mutation task; the rule itself is
 * kept free of Gradle so that it is unit-tested in build-logic.
 */
object MutationIsolation {

    /** The task names of mutation testing: PIT itself and the floor check that finalizes it (#239). */
    val MUTATION_TASKS: Set<String> = setOf("pitest", "checkMutationFloors")

    /** Whether [taskPath] is a gate that must never reach mutation testing: `:verifyAll` or any `check`. */
    fun isGate(taskPath: String): Boolean = taskPath == ":verifyAll" || taskPath.substringAfterLast(':') == "check"

    /**
     * The mutation tasks that [gate] depends on, directly or through other tasks, as sorted task paths.
     *
     * @param gate the path of the task to start from
     * @param dependencies the paths of the tasks a task path depends on or is finalized by in the graph
     */
    fun reached(gate: String, dependencies: (String) -> Collection<String>): List<String> {
        val seen = mutableSetOf(gate)
        val queue = ArrayDeque(listOf(gate))
        while (queue.isNotEmpty()) {
            dependencies(queue.removeFirst()).filter { seen.add(it) }.forEach(queue::addLast)
        }
        return seen.filter { it != gate && it.substringAfterLast(':') in MUTATION_TASKS }.sorted()
    }

    /**
     * The failure for a task graph, or null when no gate in [tasks] reaches a mutation task.
     *
     * @param tasks the paths of every task in the graph
     * @param dependencies the paths of the tasks a task path depends on in the graph
     */
    fun violation(tasks: Collection<String>, dependencies: (String) -> Collection<String>): String? {
        val found = tasks.filter(::isGate).sorted().mapNotNull { gate ->
            reached(gate, dependencies).takeIf { it.isNotEmpty() }?.let { "$gate reaches ${it.joinToString()}" }
        }
        return if (found.isEmpty()) null else
            "Mutation testing runs only in mutation-nightly.yml, never in verifyAll or check (ADR-0060, #387): " +
                found.joinToString("; ")
    }
}
