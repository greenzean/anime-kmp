package com.greenzean.animekmp.server.account

import com.greenzean.animekmp.server.auth.AuthFailure
import com.greenzean.animekmp.server.db.AppUserTable
import com.greenzean.animekmp.server.db.LinkedPlatformTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.uuid.toKotlinUuid

private val log = LoggerFactory.getLogger(LinkedPlatformRepository::class.java)

class LinkedPlatformRepository(
    private val database: Database? = null,
) {
    fun requireUser(userId: UUID) {
        val exists =
            transaction(database) {
                !AppUserTable
                    .select(AppUserTable.id)
                    .where { AppUserTable.id eq userId.toKotlinUuid() }
                    .empty()
            }
        if (!exists) {
            log.info("Auth rejected: user no longer exists")
            throw AuthFailure("User no longer exists")
        }
    }

    fun link(
        userId: UUID,
        platform: String,
    ) {
        transaction(database) {
            LinkedPlatformTable.insertIgnore {
                it[LinkedPlatformTable.userId] = userId.toKotlinUuid()
                it[LinkedPlatformTable.platform] = platform
            }
        }
    }

    fun unlink(
        userId: UUID,
        platform: String,
    ) {
        transaction(database) {
            LinkedPlatformTable.deleteWhere {
                (LinkedPlatformTable.userId eq userId.toKotlinUuid()) and (LinkedPlatformTable.platform eq platform)
            }
        }
    }

    fun platforms(userId: UUID): List<String> =
        transaction(database) {
            LinkedPlatformTable
                .select(LinkedPlatformTable.platform)
                .where { LinkedPlatformTable.userId eq userId.toKotlinUuid() }
                .map { it[LinkedPlatformTable.platform] }
                .sorted()
        }
}
