package com.venkoi.terminal.data.file

/** One immutable activation request prepared for either supported Android handoff. */
data class PreparedActivationRequest(
    val json: String,
    val suggestedFileName: String
)

