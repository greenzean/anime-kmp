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
    val serverSeq = long("server_seq").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}
