package app.lazydex.data.coil

import app.lazydex.domain.model.MediaItem
import coil3.key.Keyer
import coil3.request.Options

class CoverModelKeyer : Keyer<CoverModel> {
    override fun key(data: CoverModel, options: Options): String {
        return data.localPath?.takeIf { it.isNotBlank() }
            ?: data.url?.takeIf { it.isNotBlank() }
            ?: data.mediaId.orEmpty()
    }
}

class MediaItemKeyer : Keyer<MediaItem> {
    override fun key(data: MediaItem, options: Options): String {
        return data.coverImagePath.takeIf { it.isNotBlank() }
            ?: data.coverImageUrl?.takeIf { it.isNotBlank() }
            ?: data.id
    }
}
