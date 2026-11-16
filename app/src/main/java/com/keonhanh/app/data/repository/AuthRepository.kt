package com.keonhanh.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun signIn(email: String, pass: String): Result<FirebaseUser>
    suspend fun signUp(email: String, pass: String): Result<FirebaseUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun  sendEmailVerification() :Result<Unit>
    fun signOut()
}

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    override suspend fun signIn(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            result.user?.let { Result.success(it) }
                ?: Result.failure(Exception("Lỗi: Không lấy được thông tin user"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(email: String, pass: String): Result<FirebaseUser> {
        return try {
            // 1. Tạo tài khoản như cũ
            val result = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user

            if (user != null) {

                user.sendEmailVerification().await()

                Result.success(user)
            } else {
                Result.failure(Exception("Lỗi: Không tạo được tài khoản"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            user?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override fun signOut() {
        firebaseAuth.signOut()
    }
}

