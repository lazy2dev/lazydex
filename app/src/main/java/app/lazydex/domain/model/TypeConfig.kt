package app.lazydex.domain.model

enum class LengthUnit {
    CHARS,
    WORDS
}

data class TypeConfig(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val icon: String? = null,
    val unitName: String? = null,
    val secondaryUnitName: String? = null,
    val actionVerb: String? = null,
    val hasSecondaryProgress: Boolean? = null,
    val hasCharEstimate: Boolean? = null,
    val hasAudioSpeed: Boolean? = null,
    val lengthUnit: LengthUnit? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    val hasParts: Boolean? get() = hasSecondaryProgress
}

data class ResolvedTypeConfig(
    val typeId: String,
    val topLevelId: String,
    val displayName: String,
    val icon: String,
    val unitName: String,
    val secondaryUnitName: String?,
    val actionVerb: String,
    val hasSecondaryProgress: Boolean = false,
    val hasCharEstimate: Boolean,
    val hasAudioSpeed: Boolean,
    val lengthUnit: LengthUnit
) {
    val hasParts: Boolean get() = hasSecondaryProgress

    constructor(
        typeId: String,
        topLevelId: String,
        displayName: String,
        icon: String,
        unitName: String,
        secondaryUnitName: String?,
        actionVerb: String,
        hasParts: Boolean,
        hasCharEstimate: Boolean,
        hasAudioSpeed: Boolean,
        lengthUnit: LengthUnit,
        marker: Unit = Unit
    ) : this(
        typeId, topLevelId, displayName, icon, unitName, secondaryUnitName,
        actionVerb, hasParts, hasCharEstimate, hasAudioSpeed, lengthUnit
    )
}
