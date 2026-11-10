package com.keonhanh.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.FirebaseAuthRepository
import com.keonhanh.app.data.repository.FirestoreUserRepository
import com.keonhanh.app.data.repository.UserRepository

class AppContainer{
    val firebaseAuth : FirebaseAuth = FirebaseAuth.getInstance()
    val firestore : FirebaseFirestore = FirebaseFirestore.getInstance()

    val userRepository: UserRepository by lazy {
        FirestoreUserRepository(firestore)
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(firebaseAuth)
    }
}