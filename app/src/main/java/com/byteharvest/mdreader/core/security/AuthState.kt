package com.byteharvest.mdreader.core.security

/** Unified authentication state shared across the app. */
data class AuthState(
    val signedIn: Boolean,
    val busy: Boolean = false,
    val message: String? = null,
    val username: String? = null,
)