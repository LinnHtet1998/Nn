package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInResult {
    data class Success(val user: FirebaseUser) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun attemptSilentSignIn(context: Context): GoogleSignInResult? {
        auth.currentUser?.let { return GoogleSignInResult.Success(it) }
        val clientId = resolveWebClientId(context) ?: return null
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val result = credentialManager.getCredential(context, request)
            signInWithCredentialResult(result.credential)
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in unavailable: ${e.message}")
            null
        }
    }

    suspend fun signInInteractive(context: Context): GoogleSignInResult {
        val clientId = resolveWebClientId(context)
            ?: return GoogleSignInResult.Error("Google Sign-In client ID not configured.")
        val activity = context as? Activity
            ?: return GoogleSignInResult.Error("Activity context required for Google Sign-In.")

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            signInWithCredentialResult(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    suspend fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    private suspend fun signInWithCredentialResult(
        credential: androidx.credentials.Credential,
    ): GoogleSignInResult {
        return if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
            if (user != null) {
                GoogleSignInResult.Success(user)
            } else {
                GoogleSignInResult.Error("Firebase user missing after sign-in.")
            }
        } else {
            GoogleSignInResult.Error("Unexpected credential type.")
        }
    }

    private fun resolveWebClientId(context: Context): String? = try {
        context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
