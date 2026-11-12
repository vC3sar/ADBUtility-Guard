package com.adbutility.guard.updateguard

/**
 * Decisión final del motor inteligente Smart Update Detection.
 */
data class SmartDetectionDecision(
    val category: UpdateOperationCategory,
    val outcome: ClassificationOutcome,
    val shouldBlock: Boolean,
    val reason: String,
    val packageName: String,
    val dangerousButtons: List<ActionButtonCandidate>,
    val dialogSignature: String
)

/**
 * Orquestador de decisión inteligente del módulo Update Guard.
 * Combina análisis contextual, verificación de exclusiones obligatorias y generación
 * de firma de diálogo para control de deduplicación.
 */
class SmartUpdateDetection {

    fun evaluate(snapshot: ScreenContextSnapshot): SmartDetectionDecision {
        // Ignorar nuestra propia aplicación
        if (snapshot.packageName == "com.adbutility.guard" || snapshot.packageName.isBlank()) {
            return SmartDetectionDecision(
                category = UpdateOperationCategory.UNKNOWN,
                outcome = ClassificationOutcome.UNCERTAIN,
                shouldBlock = false,
                reason = "Paquete propio o vacío ignorado.",
                packageName = snapshot.packageName,
                dangerousButtons = emptyList(),
                dialogSignature = ""
            )
        }

        val classification = UpdateRiskClassifier.classify(snapshot)

        val shouldBlock = classification.outcome == ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE &&
            classification.category == UpdateOperationCategory.SYSTEM_UPDATE &&
            classification.signals.dangerousButtons.isNotEmpty()

        val buttonSig = classification.signals.dangerousButtons
            .joinToString(",") { "${it.normalizedLabel}@${it.boundsInScreen.left},${it.boundsInScreen.top}" }

        val signature = if (shouldBlock) {
            "${snapshot.packageName}|${snapshot.className}|$buttonSig"
        } else {
            ""
        }

        return SmartDetectionDecision(
            category = classification.category,
            outcome = classification.outcome,
            shouldBlock = shouldBlock,
            reason = classification.reason,
            packageName = snapshot.packageName,
            dangerousButtons = if (shouldBlock) classification.signals.dangerousButtons else emptyList(),
            dialogSignature = signature
        )
    }
}

