package app.lazydex.domain.model

enum class LengthUnit {
    CHARS,
    WORDS
}

data class Category(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val icon: String? = null,
    val unitName: String? = null,
    val secondaryUnitName: String? = null,
    val actionVerb: String? = null,
    val hasParts: Boolean? = null,
    val hasCharEstimate: Boolean? = null,
    val hasAudioSpeed: Boolean? = null,
    val lengthUnit: LengthUnit? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

data class ResolvedCategoryConfig(
    val categoryId: String,
    val topLevelId: String,
    val displayName: String,
    val icon: String,
    val unitName: String,
    val secondaryUnitName: String?,   // null = no secondary unit
    val actionVerb: String,
    val hasParts: Boolean,
    val hasCharEstimate: Boolean,
    val hasAudioSpeed: Boolean,
    val lengthUnit: LengthUnit
)

