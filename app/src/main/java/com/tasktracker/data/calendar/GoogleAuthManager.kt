package com.tasktracker.data.calendar

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val credentialManager = CredentialManager.create(context)

    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    val isSignedIn: Boolean get() = _signedInEmail.value != null

    private val scopes = listOf(
        CalendarScopes.CALENDAR_READONLY,
        CalendarScopes.CALENDAR_EVENTS,
    )

    suspend fun signIn(activityContext: Context): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getWebClientId())
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                _signedInEmail.value = email
                Result.success(email)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        _signedInEmail.value = null
    }

    fun getCalendarCredential(): GoogleAccountCredential? {
        val email = _signedInEmail.value ?: return null
        return GoogleAccountCredential.usingOAuth2(context, scopes)
            .setBackOff(ExponentialBackOff())
            .setSelectedAccountName(email)
    }

    private fun getWebClientId(): String {
        // This should come from google-services.json or BuildConfig
        // For now, read from string resources
        return context.getString(
            context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
        )
    }
}
