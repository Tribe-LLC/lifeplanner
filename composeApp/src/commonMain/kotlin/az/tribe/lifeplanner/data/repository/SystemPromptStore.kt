package az.tribe.lifeplanner.data.repository

object SystemPromptStore {
    private var _prompts: Map<String, String> = emptyMap()

    fun update(prompts: Map<String, String>) { _prompts = prompts }
    fun get(key: String): String? = _prompts[key]
    fun getOrDefault(key: String, default: String): String = _prompts[key] ?: default
}
