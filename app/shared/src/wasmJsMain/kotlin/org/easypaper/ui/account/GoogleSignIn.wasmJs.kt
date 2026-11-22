@file:OptIn(ExperimentalWasmJsInterop::class)

package org.easypaper.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

internal actual val googleSignInSupported: Boolean = true

private class GoogleResult {
    var callback: ((String?) -> Unit)? = null
    fun deliver(token: String?) {
        val current = callback
        callback = null
        current?.invoke(token)
    }
}

@Composable
actual fun rememberGoogleSignIn(): (onResult: (idToken: String?) -> Unit) -> Unit {
    val result = remember { GoogleResult() }
    val bridge = remember { createGoogleSignIn(GOOGLE_WEB_CLIENT_ID, result::deliver) }
    DisposableEffect(bridge) {
        onDispose { result.callback = null; disposeGoogleSignIn(bridge) }
    }
    return remember(bridge) {
        { onResult ->
            if (!googleSignInConfigured) onResult(null)
            else { result.callback = onResult; openGoogleSignIn(bridge) }
        }
    }
}

private fun openGoogleSignIn(bridge: JsAny): Unit = js("bridge.open()")
private fun disposeGoogleSignIn(bridge: JsAny): Unit = js("bridge.dispose()")

private fun createGoogleSignIn(clientId: String, onResult: (String?) -> Unit): JsAny = js("""{
    const shared = window.__easyPaperGoogleIdentity ||
        (window.__easyPaperGoogleIdentity = { loading: null, active: null, clientId: null });
    let disposed = false;
    let timer = null;
    const finish = token => {
        if (disposed || shared.active !== bridge) return;
        shared.active = null;
        clearTimeout(timer);
        if (!token && window.google && window.google.accounts) window.google.accounts.id.cancel();
        onResult(token || null);
    };
    const load = () => {
        if (window.google && window.google.accounts && window.google.accounts.id) return Promise.resolve();
        if (!shared.loading) {
            shared.loading = new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = 'https://accounts.google.com/gsi/client';
                script.async = true;
                const timeout = setTimeout(() => {
                    script.remove();
                    reject(new Error('Google sign-in script timed out'));
                }, 30000);
                script.onload = () => { clearTimeout(timeout); resolve(); };
                script.onerror = () => {
                    clearTimeout(timeout);
                    script.remove();
                    reject(new Error('Google sign-in script unavailable'));
                };
                document.head.appendChild(script);
            }).catch(error => { shared.loading = null; throw error; });
        }
        return shared.loading;
    };
    const bridge = {
        open: () => {
            if (disposed) return;
            if (shared.active) shared.active.finish(null);
            shared.active = bridge;
            // Some browsers never deliver a prompt-moment notification under FedCM.
            timer = setTimeout(() => finish(null), 120000);
            load().then(() => {
                if (disposed || shared.active !== bridge) return;
                const identity = window.google.accounts.id;
                if (shared.clientId !== clientId) {
                    identity.initialize({
                        client_id: clientId,
                        callback: response => {
                            if (shared.active) shared.active.finish(response.credential);
                        }
                    });
                    shared.clientId = clientId;
                }
                identity.prompt(moment => {
                    if (moment.isNotDisplayed() || moment.isSkippedMoment() ||
                        (moment.isDismissedMoment() && moment.getDismissedReason() !== 'credential_returned')) finish(null);
                });
            }).catch(() => finish(null));
        },
        finish,
        dispose: () => {
            disposed = true;
            clearTimeout(timer);
            if (shared.active === bridge) {
                shared.active = null;
                if (window.google && window.google.accounts) window.google.accounts.id.cancel();
            }
        }
    };
    return bridge;
}""")

