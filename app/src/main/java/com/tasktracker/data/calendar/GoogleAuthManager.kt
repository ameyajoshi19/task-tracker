package com.tasktracker.data.calendar

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    private val _signedInDisplayName = MutableStateFlow<String?>(null)
    val signedInDisplayName: StateFlow<String?> = _signedInDisplayName.asStateFlow()

    val isSignedIn: Boolean get() = _signedInEmail.value != null

    private val scopes = listOf(
        CalendarScopes.CALENDAR,
    )

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(CalendarScopes.CALENDAR),
            )
            .requestServerAuthCode(getWebClientId())
            .build()
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    init {
        // Restore sign-in state from last signed-in account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            _signedInEmail.value = account.email
            _signedInDisplayName.value = account.displayName
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount): Result<String> {
        val email = account.email ?: return Result.failure(
            IllegalStateException("No email in sign-in result")
        )
        _signedInEmail.value = email
        _signedInDisplayName.value = account.displayName
        return Result.success(email)
    }

    suspend fun signOut() {
        googleSignInClient.signOut().await()
        _signedInEmail.value = null
        _signedInDisplayName.value = null
    }

    fun getCalendarCredential(): GoogleAccountCredential? {
        val email = _signedInEmail.value ?: return null
        return GoogleAccountCredential.usingOAuth2(context, scopes)
            .setBackOff(ExponentialBackOff())
            .setSelectedAccountName(email)
    }

    private fun getWebClientId(): String {
        return context.getString(
            context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
        )
    }
}
