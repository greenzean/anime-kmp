package com.greenzean.animekmp.server.auth

import com.greenzean.animekmp.server.db.AppUserTable
import com.greenzean.animekmp.server.db.RefreshTokenTable
import com.greenzean.animekmp.server.db.SignInNonceTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private const val NONCE_BYTES = 32
private const val NONCE_SEPARATOR = "."
private val NONCE_TTL: Duration = Duration.ofMinutes(5)

sealed interface RotationResult {
    data class Rotated(
        val userId: UUID,
        val familyId: UUID,
    ) : RotationResult

    data object Invalid : RotationResult

    data object ReuseDetected : RotationResult
}

enum class NoncePurpose(
    val prefix: String,
) {
    SIGN_IN("s"),
    DELETE_ACCOUNT("d"),
}

class AuthRepository(
    private val database: Database? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    fun issueNonce(purpose: NoncePurpose): String {
        val nonce =
            purpose.prefix + NONCE_SEPARATOR +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteArray(NONCE_BYTES).also(random::nextBytes),
                )
        transaction(database) {
            SignInNonceTable.deleteWhere { expiresAt less now().toOffset() }
            SignInNonceTable.insert {
                it[SignInNonceTable.nonce] = nonce
                it[expiresAt] = now().plus(NONCE_TTL).toOffset()
            }
        }
        return nonce
    }

    fun consumeNonce(
        nonce: String,
        purpose: NoncePurpose,
    ): Boolean =
        nonce.startsWith(purpose.prefix + NONCE_SEPARATOR) &&
            transaction(database) {
                SignInNonceTable.deleteWhere {
                    (SignInNonceTable.nonce eq nonce) and (expiresAt greater now().toOffset())
                } ==
                    1
            }

    fun userIdForSub(sub: String): UUID =
        transaction(database) {
            AppUserTable.insertIgnore { it[googleSub] = sub }
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.googleSub eq sub }
                .single()[AppUserTable.id]
                .toJavaUuid()
        }

    fun findUserIdBySub(sub: String): UUID? =
        transaction(database) {
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.googleSub eq sub }
                .singleOrNull()
                ?.get(AppUserTable.id)
                ?.toJavaUuid()
        }

    fun deleteUser(userId: UUID): Boolean =
        transaction(database) { AppUserTable.deleteWhere { id eq userId.toKotlinUuid() } == 1 }

    fun storeRefreshToken(
        userId: UUID,
        familyId: UUID,
        tokenHash: ByteArray,
        expiresAt: Instant,
    ) {
        transaction(database) { insertRefresh(userId, familyId, tokenHash, expiresAt) }
    }

    fun rotate(
        presentedHash: ByteArray,
        successorHash: ByteArray,
        successorExpiresAt: Instant,
    ): RotationResult =
        transaction(database) {
            val row =
                RefreshTokenTable
                    .selectAll()
                    .where { RefreshTokenTable.tokenHash eq presentedHash }
                    .forUpdate()
                    .singleOrNull()
            val now = now().toOffset()
            when {
                row == null || row[RefreshTokenTable.expiresAt] <= now -> {
                    RotationResult.Invalid
                }

                row[RefreshTokenTable.revokedAt] != null -> {
                    RefreshTokenTable.update({
                        (RefreshTokenTable.familyId eq row[RefreshTokenTable.familyId]) and
                            RefreshTokenTable.revokedAt.isNull()
                    }) { it[revokedAt] = now }
                    RotationResult.ReuseDetected
                }

                else -> {
                    RefreshTokenTable.update({ RefreshTokenTable.id eq row[RefreshTokenTable.id] }) {
                        it[revokedAt] = now
                    }
                    val userId = row[RefreshTokenTable.userId].toJavaUuid()
                    val familyId = row[RefreshTokenTable.familyId].toJavaUuid()
                    insertRefresh(userId, familyId, successorHash, successorExpiresAt)
                    RotationResult.Rotated(userId, familyId)
                }
            }
        }

    fun revokeFamily(tokenHash: ByteArray) {
        transaction(database) {
            val familyId =
                RefreshTokenTable
                    .select(RefreshTokenTable.familyId)
                    .where { RefreshTokenTable.tokenHash eq tokenHash }
                    .singleOrNull()
                    ?.get(RefreshTokenTable.familyId) ?: return@transaction
            RefreshTokenTable.update({
                (RefreshTokenTable.familyId eq familyId) and RefreshTokenTable.revokedAt.isNull()
            }) { it[revokedAt] = now().toOffset() }
        }
    }

    private fun insertRefresh(
        userId: UUID,
        familyId: UUID,
        tokenHash: ByteArray,
        expiresAt: Instant,
    ) {
        RefreshTokenTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[RefreshTokenTable.userId] = userId.toKotlinUuid()
            it[RefreshTokenTable.familyId] = familyId.toKotlinUuid()
            it[RefreshTokenTable.tokenHash] = tokenHash
            it[createdAt] = now().toOffset()
            it[RefreshTokenTable.expiresAt] = expiresAt.toOffset()
        }
    }

    private fun now(): Instant = clock.instant()

    private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
