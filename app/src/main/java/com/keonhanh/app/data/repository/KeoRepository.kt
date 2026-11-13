package com.keonhanh.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.remote.snapshotFlow
import com.keonhanh.app.data.remote.toKeoDoc
import com.keonhanh.app.data.remote.toNewKeoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

interface KeoRepository {
    fun observeKeo(keoId: String): Flow<KeoDoc?>
    suspend fun getKeos(keoIds: List<String>): List<KeoDoc>
    fun observeActiveKeos(): Flow<List<KeoDoc>>
    suspend fun createKeo(draft: KeoDraft, hostId: String, hostName: String): String
    suspend fun updateKeo(keoId: String, draft: KeoDraft)
    suspend fun deleteKeo(keoId: String)
    suspend fun findByInviteCode(code: String): KeoDoc?
}

class FirestoreKeoRepository(
    private val firestore: FirebaseFirestore,
) : KeoRepository {

    private val keos get() = firestore.collection(FirestorePaths.KEOS)

    override fun observeKeo(keoId: String): Flow<KeoDoc?> =
        keos.document(keoId).snapshotFlow().map { it.toKeoDoc() }

    override suspend fun getKeos(keoIds: List<String>): List<KeoDoc> =
        keoIds.distinct().chunked(30).flatMap { chunk ->
            keos.whereIn(FieldPath.documentId(), chunk).get().await()
                .documents.mapNotNull { it.toKeoDoc() }
        }

    override fun observeActiveKeos(): Flow<List<KeoDoc>> {
        val from = Timestamp(Date(System.currentTimeMillis() - 15 * 60_000L))
        return keos.whereGreaterThan("startAt", from)
            .orderBy("startAt")
            .limit(50)
            .snapshotFlow()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toKeoDoc() } }
    }

    override suspend fun createKeo(draft: KeoDraft, hostId: String, hostName: String): String {
        val keoRef = keos.document()
        val code = taoMaMoiKhongTrung()

        val batch = firestore.batch()
        batch.set(keoRef, draft.toNewKeoMap(hostId, code))

        val hostMemberRef = keoRef.collection(FirestorePaths.MEMBERS).document(hostId)
        val hostMemberMap = mapOf(
            "uid" to hostId,
            "name" to hostName,
            "role" to "HOST",
            "state" to "JOINED",
            "joinedAt" to FieldValue.serverTimestamp()
        )
        batch.set(hostMemberRef, hostMemberMap)

        batch.commit().await()
        return keoRef.id
    }

    override suspend fun updateKeo(keoId: String, draft: KeoDraft) {
        val updateMap = mapOf(
            "title" to draft.title,
            "sport" to draft.sport.name,
            "placeName" to draft.placeName,
            "lat" to draft.lat,
            "lng" to draft.lng,
            "startAt" to draft.startAt,
            "deadlineAt" to draft.deadlineAt,
            "capacity" to draft.capacity,
            "minTrust" to draft.minTrust,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        keos.document(keoId).update(updateMap).await()
    }

    override suspend fun deleteKeo(keoId: String) {
        val keoRef = keos.document(keoId)
        val members = keoRef.collection(FirestorePaths.MEMBERS).get().await()
        val messages = keoRef.collection(FirestorePaths.MESSAGES).get().await()

        val batch = firestore.batch()
        members.documents.forEach { batch.delete(it.reference) }
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(keoRef)
        batch.commit().await()
    }

    override suspend fun findByInviteCode(code: String): KeoDoc? {
        return keos.whereEqualTo("inviteCode", code.trim().uppercase())
            .limit(1).get().await()
            .documents.firstOrNull()?.toKeoDoc()
    }

    private suspend fun taoMaMoiKhongTrung(): String {
        val charPool = ('A'..'Z') + ('0'..'9')
        var isUnique = false
        var code = ""
        while (!isUnique) {
            code = (1..6).map { Random.nextInt(0, charPool.size) }.map(charPool::get).joinToString("")
            val check = keos.whereEqualTo("inviteCode", code).limit(1).get().await()
            if (check.isEmpty) isUnique = true
        }
        return code
    }
}