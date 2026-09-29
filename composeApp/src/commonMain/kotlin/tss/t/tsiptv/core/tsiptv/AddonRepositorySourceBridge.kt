package tss.t.tsiptv.core.tsiptv

import tss.t.tsiptv.core.stremio.AddonPreview
import tss.t.tsiptv.core.stremio.AddonPreviewResult
import tss.t.tsiptv.core.stremio.AddonRepository

/** [SourceAddonBridge] over F2's [AddonRepository]: blocklist, manifest validation, encrypted storage. */
class AddonRepositorySourceBridge(private val repository: AddonRepository) : SourceAddonBridge {
    override suspend fun probe(manifestUrl: String): AddonProbe = try {
        when (val result = repository.preview(manifestUrl)) {
            is AddonPreviewResult.Ready -> AddonProbe.Ready(
                addonId = result.preview.manifest.id,
                isAdult = result.preview.isAdult,
                manifestJson = result.preview.rawManifestJson,
                handle = result.preview,
            )
            AddonPreviewResult.Blocked -> AddonProbe.Failed("blocked")
            AddonPreviewResult.Unreachable -> AddonProbe.Failed("unreachable")
            is AddonPreviewResult.InvalidManifest -> AddonProbe.Failed("invalid_manifest")
            is AddonPreviewResult.NeedsConfiguration -> AddonProbe.Failed("needs_configuration")
            is AddonPreviewResult.InvalidUrl -> AddonProbe.Failed("invalid_url")
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        AddonProbe.Failed("unreachable")
    }

    override suspend fun install(probe: AddonProbe.Ready, ownerPlaylistId: String): String =
        repository.installOwned(probe.handle as AddonPreview, ownerPlaylistId).id

    override suspend fun removeOwned(ownerPlaylistId: String, keep: Set<String>) =
        repository.removeOwnedBy(ownerPlaylistId, keep)

    override suspend fun ownedBy(ownerPlaylistId: String): Set<String> = repository.ownedBy(ownerPlaylistId)
}
