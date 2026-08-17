package com.venkoi.terminal.data.file

import javax.inject.Inject

/** Coordinates activation-request handoff only; it deliberately owns no license or sales state. */
class ActivationRequestDeliveryCoordinator @Inject constructor() {
    suspend fun <Destination> save(
        destination: Destination?,
        prepared: PreparedActivationRequest,
        write: suspend (Destination, String) -> Boolean
    ): ActivationDeliveryResult {
        if (destination == null) return ActivationDeliveryResult.Cancelled
        return try {
            if (write(destination, prepared.json)) ActivationDeliveryResult.Success
            else ActivationDeliveryResult.Failed
        } catch (_: Exception) {
            ActivationDeliveryResult.Failed
        }
    }

    suspend fun share(
        prepared: PreparedActivationRequest,
        handoff: suspend (String, String) -> Boolean
    ): ActivationDeliveryResult = try {
        if (handoff(prepared.json, prepared.suggestedFileName)) ActivationDeliveryResult.Success
        else ActivationDeliveryResult.Failed
    } catch (_: Exception) {
        ActivationDeliveryResult.Failed
    }
}

enum class ActivationDeliveryResult { Success, Cancelled, Failed }

