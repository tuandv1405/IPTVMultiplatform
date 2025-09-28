package tss.t.tsiptv.core.parser.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Serializable models for JSON IPTV playlists.
 * These models are used with Kotlin Serialization to parse JSON content.
 */

/**
 * Main playlist model that can handle different JSON formats
 */
@Serializable
data class JSONPlaylist(
    val name: String = "IPTV Playlist",
    val id: String? = null,
    val url: String? = null,
    val channels: List<JSONChannel>? = null,
    val groups: List<JSONGroup>? = null,
    val description: String? = null,
    val epgUrl: String? = null,
    val image: JSONImage? = null
) {
    /**
     * Convert to IPTVPlaylist model
     */
    fun toIPTVPlaylist(): IPTVPlaylist {
        val allChannels = mutableListOf<IPTVChannel>()
        val allGroups = mutableSetOf<IPTVGroup>()
        
        // Add channel from the root level
        channels?.forEach { jsonChannel ->
            val channel = jsonChannel.toIPTVChannel()
            allChannels.add(channel)
            
            // Add group if present
            if (jsonChannel.groupTitle != null) {
                val groupId = jsonChannel.groupId ?: jsonChannel.groupTitle.replace(" ", "_").lowercase()
                allGroups.add(IPTVGroup(id = groupId, title = jsonChannel.groupTitle))
            }
        }
        
        // Add channel from groups
        groups?.forEach { jsonGroup ->
            val groupId = jsonGroup.id ?: jsonGroup.name.replace(" ", "_").lowercase()
            allGroups.add(IPTVGroup(id = groupId, title = jsonGroup.name))
            
            jsonGroup.channels?.forEach { jsonChannel ->
                val channel = jsonChannel.toIPTVChannel(groupTitle = jsonGroup.name, groupId = groupId)
                allChannels.add(channel)
            }
        }
        
        return IPTVPlaylist(
            name = name,
            channels = allChannels,
            groups = allGroups.toList(),
            epgUrl = epgUrl
        )
    }
}

/**
 * Channel model that can handle different JSON formats
 */
@Serializable
data class JSONChannel(
    val id: String,
    val name: String,
    
    // Handle different ways the URL might be represented
    val url: String? = null,
    @SerialName("remote_data")
    val remoteData: JSONRemoteData? = null,
    
    // Handle different ways the logo might be represented
    @JsonNames("logo", "logoUrl")
    val logoUrl: String? = null,
    val image: JSONImage? = null,
    
    // Handle different ways the group might be represented
    @JsonNames("group", "groupTitle")
    val groupTitle: String? = null,
    val groupId: String? = null,
    
    // Handle different ways the EPG ID might be represented
    @JsonNames("epg", "epgId")
    val epgId: String? = null,
    
    val description: String? = null,
    val attributes: Map<String, String>? = null
) {
    /**
     * Convert to IPTVChannel model
     */
    fun toIPTVChannel(groupTitle: String? = null, groupId: String? = null): IPTVChannel {
        val effectiveUrl = url ?: remoteData?.url ?: ""
        val effectiveLogoUrl = logoUrl ?: image?.url
        val effectiveGroupTitle = groupTitle ?: this.groupTitle
        val effectiveGroupId = groupId ?: this.groupId ?: 
                              (effectiveGroupTitle?.replace(" ", "_")?.lowercase())
        
        return IPTVChannel(
            id = id,
            name = name,
            url = effectiveUrl,
            logoUrl = effectiveLogoUrl,
            groupTitle = effectiveGroupTitle,
            groupId = effectiveGroupId,
            epgId = epgId,
            attributes = attributes ?: emptyMap()
        )
    }
}

/**
 * Group model
 */
@Serializable
data class JSONGroup(
    val id: String? = null,
    val name: String,
    val channels: List<JSONChannel>? = null,
    val display: String? = null
)

/**
 * Remote data model for channel that use remote_data.url instead of direct url
 */
@Serializable
data class JSONRemoteData(
    val url: String,
    val external: Boolean? = null
)

/**
 * Image model for channel that use image.url instead of direct logoUrl
 */
@Serializable
data class JSONImage(
    val url: String,
    val type: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

/**
 * Array of channel format
 */
@Serializable
data class JSONChannelArray(
    val channels: List<JSONChannel>
)

/**
 * Array of groups format
 */
@Serializable
data class JSONGroupArray(
    val groups: List<JSONGroup>
)