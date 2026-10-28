package com.greenzean.animekmp.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object AnimeTable : Table("anime") {
    val id = long("id").autoIncrement()
    val malId = integer("mal_id").uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object ListEntryTable : Table("list_entry") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val animeId = long("anime_id").references(AnimeTable.id)
    val status = text("status")
    val updatedAt = timestampWithTimeZone("updated_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val progress = integer("progress")
    val score = integer("score").nullable()
    val serverSeq = long("server_seq").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

object AppUserTable : Table("app_user") {
    val id = uuid("id").databaseGenerated()
    val googleSub = text("google_sub").uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object SignInNonceTable : Table("sign_in_nonce") {
    val nonce = text("nonce")
    val expiresAt = timestampWithTimeZone("expires_at")

    override val primaryKey = PrimaryKey(nonce)
}

object RefreshTokenTable : Table("refresh_token") {
    val id = uuid("id")
    val userId = uuid("user_id").references(AppUserTable.id)
    val familyId = uuid("family_id")
    val tokenHash = binary("token_hash").uniqueIndex()
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
