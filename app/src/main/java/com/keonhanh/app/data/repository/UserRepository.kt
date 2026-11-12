package com.keonhanh.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.model.UserProfile
import kotlinx.coroutines.tasks.await

interface UserRepository {
    suspend fun createUserProfile(userProfile: UserProfile): Result<Unit>
    suspend fun getUserProfile(uid: String): Result<UserProfile?>
}

class FirestoreUserRepository(
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun createUserProfile(userProfile: UserProfile): Result<Unit> {
        return try {
            // Tạo document trong collection "users" với ID chính là uid
            firestore.collection("users")
                .document(userProfile.uid)
                .set(userProfile)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                val profile = document.toObject(UserProfile::class.java)
                Result.success(profile)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

