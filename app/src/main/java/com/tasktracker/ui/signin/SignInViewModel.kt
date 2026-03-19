package com.tasktracker.ui.signin

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.tasktracker.data.calendar.GoogleAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    val authManager: GoogleAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun setSigningIn() {
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val result = authManager.handleSignInResult(account)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSigningIn = false, signedIn = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = error.message)
                    }
                },
            )
        } catch (e: ApiException) {
            _uiState.update {
                it.copy(isSigningIn = false, signInError = "Sign-in failed: ${e.statusCode}")
            }
        }
    }
}
