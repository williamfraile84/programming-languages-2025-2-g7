package com.jukhg10.hanzimemo.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY pinyin ASC")
    fun getAllWords(): Flow<List<Word>>

    @Query("""
        SELECT * FROM words 
        WHERE simplified LIKE '%' || :query || '%' 
        OR traditional LIKE '%' || :query || '%' 
        OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE '%' || :query || '%' 
        OR definition LIKE '%' || :query || '%'
        ORDER BY
            /* Priority 1: Smart Exact Match for Pinyin */
            CASE 
                WHEN REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') = :query /* Exact match */
                OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE :query || ' /%' /* Starts with the pinyin */
                OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE '%/ ' || :query || ' /%' /* Pinyin is in the middle */
                OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE '%/ ' || :query /* Ends with the pinyin */
                THEN 0 
                ELSE 1 
            END,
            /* Priority 2: "Starts-with" match on any field */
            CASE
                WHEN REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE :query || '%' THEN 0
                ELSE 1
            END,
            /* Priority 3: Pinyin match over definition match */
            CASE
                WHEN REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') LIKE '%' || :query || '%' THEN 0
                ELSE 1
            END,
            /* Final tie-breakers */
            LENGTH(simplified) ASC,
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(pinyin, '1', ''), '2', ''), '3', ''), '4', ''), '5', '') ASC
    """)
    fun searchWords(query: String): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?

    // --- Token-based search with SQL efficiency for AND semantics ---
    @RawQuery(observedEntities = [Word::class])
    fun searchWordsByTokens(query: SupportSQLiteQuery): Flow<List<Word>>
}