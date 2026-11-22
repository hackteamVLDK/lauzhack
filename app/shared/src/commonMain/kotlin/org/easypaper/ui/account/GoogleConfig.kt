package org.easypaper.ui.account

// Replace with the Google OAuth WEB client ID; allow this same ID on the account server.
// Android also needs its package name and signing certificate registered in Google Cloud.
const val GOOGLE_WEB_CLIENT_ID = "130201553684-euc2oh2te84dor7cdo2n4kre6cuijm1l.apps.googleusercontent.com"

internal val googleSignInConfigured: Boolean
    get() = GOOGLE_WEB_CLIENT_ID.isNotBlank() && !GOOGLE_WEB_CLIENT_ID.startsWith("REPLACE_WITH_")
