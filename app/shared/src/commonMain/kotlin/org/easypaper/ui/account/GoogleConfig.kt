package org.easypaper.ui.account

// Replace with the Google OAuth WEB client ID; allow this same ID on the account server.
// Android also needs its package name and signing certificate registered in Google Cloud.
const val GOOGLE_WEB_CLIENT_ID = "130201553684-euc2oh2te84dor7cdo2n4kre6cuijm1l.apps.googleusercontent.com"

// Public OAuth Desktop app client ID. The client secret belongs only on the account server.
const val GOOGLE_DESKTOP_CLIENT_ID = ""

internal expect val googleSignInClientId: String

internal val googleSignInConfigured: Boolean
    get() = googleSignInClientId.isNotBlank() && !googleSignInClientId.startsWith("REPLACE_WITH_")
