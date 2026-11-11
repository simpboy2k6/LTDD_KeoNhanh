package com.keonhanh.app.ui.mykeo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
 * Nghe các dòng members của mình ở mọi kèo (collectionGroup), rồi đọc thông tin các kèo đó một lần.
 * Ai vào/rời kèo ở màn khác thì danh sách ở đây tự đổi theo.
 */
class MyKeoViewModel(
    memberRepository: MemberRepository,
    keoRepository: KeoRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<UiState<MyKeoUi>> = run {
        val uid = authRepository.currentUser?.uid
        val source: Flow<UiState<MyKeoUi>> = if (uid == null) {
            flowOf(UiState.Error(UiText.Res(R.string.error_not_signed_in)))
        } else {
            memberRepository.observeMyMemberships(uid).map { memberships ->
                // Yêu cầu "Xin vào" chưa được duyệt thì chưa tính là kèo của mình
                val mine = memberships.filter { it.member.state == MemberState.JOINED }
                val myRowByKeo = mine.associate { it.keoId to it.member }
                val now = System.currentTimeMillis()
                val keos = keoRepository.getKeos(myRowByKeo.keys.toList())
                    .sortedBy { it.startAt }
                    .map { doc -> doc.toKeo(members = listOfNotNull(myRowByKeo[doc.id]), myUid = uid, nowMillis = now) }
                UiState.Success(
                    MyKeoUi(
                        joined = keos.filter { it.myRole == MyRole.MEMBER && it.status != KeoStatus.ENDED },
                        hosting = keos.filter { it.myRole == MyRole.HOST && it.status != KeoStatus.ENDED },
                        done = keos.filter { it.status == KeoStatus.ENDED },
                    ),
                )
            }
        }
        source
            .catch { emit(UiState.Error(it.toUiText())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
    }
}
