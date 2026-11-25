package com.keonhanh.app.ui.mykeo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 3 tab của S17. */
data class MyKeoUi(
    val joined: List<Keo>,
    val hosting: List<Keo>,
    val done: List<Keo>,
)

/**
 * ViewModel của S17 Kèo của tôi (Phong Hào).
 * Lắng nghe thay đổi tài khoản (AuthStateListener) và các dòng members của user hiện tại.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyKeoViewModel(
    memberRepository: MemberRepository,
    keoRepository: KeoRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val currentUidFlow: Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        trySend(auth.currentUser?.uid)
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val state: StateFlow<UiState<MyKeoUi>> = currentUidFlow
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(UiState.Error(UiText.Res(R.string.error_not_signed_in)))
            } else {
                memberRepository.observeMyMemberships(uid).map { memberships ->
                    // Yêu cầu "Xin vào" chưa được duyệt thì chưa tính là kèo của mình
                    val mine = memberships.filter { it.member.state == MemberState.JOINED }
                    val myRowByKeo = mine.associate { it.keoId to it.member }
                    val now = System.currentTimeMillis()
                    val keos = keoRepository.getKeos(myRowByKeo.keys.toList())
                        .sortedBy { it.startAt }
                        .map { doc ->
                            doc.toKeo(
                                members = listOfNotNull(myRowByKeo[doc.id]),
                                myUid = uid,
                                nowMillis = now
                            )
                        }
                    UiState.Success(
                        MyKeoUi(
                            joined = keos.filter { it.myRole == MyRole.MEMBER && it.status != KeoStatus.ENDED },
                            hosting = keos.filter { it.myRole == MyRole.HOST && it.status != KeoStatus.ENDED },
                            done = keos.filter { it.status == KeoStatus.ENDED },
                        ),
                    )
                }
            }
        }
        .catch { emit(UiState.Error(it.toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}