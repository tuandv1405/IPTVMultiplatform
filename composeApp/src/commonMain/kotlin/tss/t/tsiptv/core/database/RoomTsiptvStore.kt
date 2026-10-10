package tss.t.tsiptv.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.database.entity.toEntity
import tss.t.tsiptv.core.database.entity.toRecord
import tss.t.tsiptv.core.database.entity.toProgramEntity
import tss.t.tsiptv.core.tsiptv.TsiptvEpisodeRecord
import tss.t.tsiptv.core.tsiptv.TsiptvGuideIds
import tss.t.tsiptv.core.tsiptv.TsiptvIncludeRecord
import tss.t.tsiptv.core.tsiptv.TsiptvSourceRecord
import tss.t.tsiptv.core.tsiptv.TsiptvStore
import tss.t.tsiptv.core.tsiptv.TsiptvStoredContent
import tss.t.tsiptv.core.tsiptv.TsiptvVodItemRecord

/** Room-backed F3 tables (v6). */
class RoomTsiptvStore(
    private val database: AppDatabase,
    private val iptv: RoomIPTVDatabase,
) : TsiptvStore {
    private val dao get() = database.tsiptvDao()

    override fun observeSource(playlistId: String): Flow<TsiptvSourceRecord?> =
        dao.observeSource(playlistId).map { it?.toRecord() }

    override fun observeSources(): Flow<List<TsiptvSourceRecord>> =
        dao.observeSources().map { rows -> rows.map { it.toRecord() } }

    override suspend fun getSource(playlistId: String): TsiptvSourceRecord? = dao.getSource(playlistId)?.toRecord()

    override suspend fun getSourceBySourceId(sourceId: String): TsiptvSourceRecord? =
        dao.getSourceBySourceId(sourceId)?.toRecord()

    override suspend fun upsertSource(record: TsiptvSourceRecord) = dao.upsertSource(record.toEntity())

    override suspend fun getIncludes(playlistId: String): List<TsiptvIncludeRecord> =
        dao.getIncludes(playlistId).map { it.toRecord() }

    override fun observeIncludes(playlistId: String): Flow<List<TsiptvIncludeRecord>> =
        dao.observeIncludes(playlistId).map { rows -> rows.map { it.toRecord() } }

    override suspend fun upsertIncludes(records: List<TsiptvIncludeRecord>) =
        dao.upsertIncludes(records.map { it.toEntity() })

    override fun observeVodItems(playlistId: String): Flow<List<TsiptvVodItemRecord>> =
        dao.observeVodItems(playlistId).map { rows -> rows.map { it.toRecord() } }

    override suspend fun getVodItems(playlistId: String): List<TsiptvVodItemRecord> =
        dao.getVodItems(playlistId).map { it.toRecord() }

    override suspend fun getVodItem(playlistId: String, itemId: String): TsiptvVodItemRecord? =
        dao.getVodItem(TsiptvStore.rowId(playlistId, itemId))?.toRecord()

    override suspend fun getEpisodes(playlistId: String, seriesItemId: String): List<TsiptvEpisodeRecord> =
        dao.getEpisodes(playlistId, seriesItemId).map { it.toRecord() }

    override suspend fun getAllEpisodes(playlistId: String): List<TsiptvEpisodeRecord> =
        dao.getAllEpisodes(playlistId).map { it.toRecord() }

    override suspend fun getEpisode(playlistId: String, episodeId: String): TsiptvEpisodeRecord? =
        dao.getEpisode(TsiptvStore.rowId(playlistId, episodeId))?.toRecord()

    override suspend fun countEpisodes(playlistId: String): Int = dao.countEpisodes(playlistId)

    override suspend fun replaceSourceContent(content: TsiptvStoredContent) {
        val playlistId = content.playlist.id
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // Playlist row, categories and channels exactly as any playlist import stores them
                // (favourites, last-watched and history carried over by channel id).
                iptv.replacePlaylistContentLocked(
                    playlist = content.playlist,
                    categoriesIn = content.categories,
                    channelsIn = content.channels,
                    attributesIn = emptyMap(),
                    legacyIdsIn = emptyMap(),
                )
                dao.upsertSource(content.source.toEntity())
                dao.deleteIncludes(playlistId)
                dao.upsertIncludes(content.includes.map { it.toEntity() })
                dao.deleteVodItems(playlistId)
                dao.insertVodItems(content.vodItems.map { it.toEntity() })
                dao.deleteEpisodes(playlistId)
                dao.insertEpisodes(content.episodes.map { it.toEntity() })
                // Guide programmes, per guide, in the same transaction (QC F3 #18).
                val guides = content.guides
                if (!guides.isEmpty) {
                    val programs = iptv.programDao
                    programs.deleteUnprefixedPrograms(playlistId)
                    for (path in guides.drop + guides.replace.keys) {
                        programs.deleteProgramsWithPrefix(playlistId, TsiptvGuideIds.prefix(playlistId, path))
                    }
                    for ((path, list) in guides.replace) {
                        programs.insertPrograms(
                            list.map { it.copy(id = TsiptvGuideIds.programId(playlistId, path, it.id)).toProgramEntity(playlistId) }
                        )
                    }
                }
            }
        }
    }
}
