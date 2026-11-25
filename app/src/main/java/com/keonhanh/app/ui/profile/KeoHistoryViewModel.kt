package com.keonhanh.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.remote.Attendance
import com.keonhanh.app.data.remote.MemberDoc
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Một dòng kèo trong Lịch sử kèo, kèm biến cho biết mình đã check-in hay chưa. */
data class HistoryKeoItem(
    val keo: Keo,
    val checkedIn: Boolean,
)

/** Dữ liệu hiển thị cho màn Lịch sử kèo: 3 con số thống kê + danh sách kèo đã xong. */
data class KeoHistoryUi(
    val totalCount: Int,
    val attendedCount: Int,
    val absentCount: Int,
    val items: List<HistoryKeoItem>,
)

/** ViewModel của màn Lịch sử kèo (mở từ Hồ sơ). */
class KeoHistoryViewModel(
    private val memberRepository: MemberRepository,
    private val keoRepository: KeoRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<KeoHistoryUi>>(UiState.Loading)
    val state: StateFlow<UiState<KeoHistoryUi>> = _state.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            _state.value = UiState.Error(UiText.Res(R.string.error_not_signed_in))
            return
        }

        viewModelScope.launch {
            memberRepository.observeMyMemberships(uid)
                .catch { error ->
                    _state.value = UiState.Error(error.toUiText())
                }
                .collect { memberships ->
                    try {
                        // Bước 1: Lọc những kèo mình đã chính thức tham gia (JOINED)
                        val myMembersMap = HashMap<String, MemberDoc>()
                        val joinedKeoIds = ArrayList<String>()
                        for (membership in memberships) {
                            if (membership.member.state == MemberState.JOINED) {
                                myMembersMap[membership.keoId] = membership.member
                                joinedKeoIds.add(membership.keoId)
                            }
                        }

                        // Bước 2: Lấy thông tin chi tiết các kèo đó từ Firestore
                        val allKeoDocs = keoRepository.getKeos(joinedKeoIds)

                        // Bước 3: Chỉ giữ lại các kèo đã kết thúc và sắp xếp kèo mới nhất lên đầu
                        val now = System.currentTimeMillis()
                        val endedKeos = allKeoDocs
                            .filter { it.effectiveStatus(now) == KeoStatus.ENDED }
                            .sortedByDescending { it.startAt }

                        // Bước 4: Đếm số kèo đã tham gia / vắng mặt và tạo danh sách hiển thị
                        val historyItems = ArrayList<HistoryKeoItem>()
                        var attendedCount = 0

                        for (doc in endedKeos) {
                            val myRow = myMembersMap[doc.id]
                            val isCheckedIn = myRow != null &&
                                (myRow.checkInAt != null || myRow.attendance == Attendance.PRESENT)

                            if (isCheckedIn) {
                                attendedCount++
                            }

                            val keo = doc.toKeo(
                                members = if (myRow != null) listOf(myRow) else emptyList(),
                                myUid = uid,
                                nowMillis = now,
                            )
                            historyItems.add(HistoryKeoItem(keo = keo, checkedIn = isCheckedIn))
                        }

                        val totalCount = historyItems.size
                        val absentCount = totalCount - attendedCount

                        _state.value = UiState.Success(
                            KeoHistoryUi(
                                totalCount = totalCount,
                                attendedCount = attendedCount,
                                absentCount = absentCount,
                                items = historyItems,
                            ),
                        )
                    } catch (error: Exception) {
                        _state.value = UiState.Error(error.toUiText())
                    }
                }
        }
    }
}
