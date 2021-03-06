package io.casey.musikcube.remote.ui.view

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.support.annotation.AttrRes
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.casey.musikcube.remote.Application
import io.casey.musikcube.remote.R
import io.casey.musikcube.remote.playback.*
import io.casey.musikcube.remote.ui.activity.AlbumBrowseActivity
import io.casey.musikcube.remote.ui.activity.TrackListActivity
import io.casey.musikcube.remote.ui.extension.getColorCompat
import io.casey.musikcube.remote.ui.model.AlbumArtModel
import io.casey.musikcube.remote.util.Strings
import io.casey.musikcube.remote.websocket.Messages
import io.casey.musikcube.remote.websocket.Prefs
import io.casey.musikcube.remote.websocket.SocketMessage
import io.casey.musikcube.remote.websocket.WebSocketService
import org.json.JSONArray
import javax.inject.Inject

class MainMetadataView : FrameLayout {
    @Inject lateinit var wss: WebSocketService
    private var prefs: SharedPreferences? = null

    private var isPaused = true
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var album: TextView
    private lateinit var volume: TextView
    private lateinit var titleWithArt: TextView
    private lateinit var artistAndAlbumWithArt: TextView
    private lateinit var volumeWithArt: TextView
    private lateinit var mainTrackMetadataWithAlbumArt: View
    private lateinit var mainTrackMetadataNoAlbumArt: View
    private lateinit var buffering: View
    private lateinit var albumArtImageView: ImageView

    private enum class DisplayMode {
        Artwork, NoArtwork, Stopped
    }

    private var albumArtModel = AlbumArtModel.empty()
    private var lastDisplayMode = DisplayMode.Stopped
    private var lastArtworkUrl: String? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    fun onResume() {
        this.wss.addClient(wssClient)
        isPaused = false
    }

    fun onPause() {
        this.wss.removeClient(wssClient)
        isPaused = true
    }

    fun clear() {
        if (!isPaused) {
            albumArtModel = AlbumArtModel.empty()
            updateAlbumArt()
        }
    }

    fun hide() {
        visibility = View.GONE
    }

    fun refresh() {
        if (!isPaused) {
            visibility = View.VISIBLE

            val playback = playbackService

            val buffering = playback.playbackState == PlaybackState.Buffering
            val streaming = playback is StreamingPlaybackService

            val artist = playback.getTrackString(Metadata.Track.ARTIST, "")
            val album = playback.getTrackString(Metadata.Track.ALBUM, "")
            val title = playback.getTrackString(Metadata.Track.TITLE, "")

            /* we don't display the volume amount when we're streaming -- the system has
            overlays for drawing volume. */
            if (streaming) {
                volume.visibility = View.GONE
                volumeWithArt.visibility = View.GONE
            }
            else {
                val volume = getString(R.string.status_volume, Math.round(playback.volume * 100))
                this.volume.visibility = View.VISIBLE
                this.volumeWithArt.visibility = View.VISIBLE
                this.volume.text = volume
                this.volumeWithArt.text = volume
            }

            this.title.text = if (Strings.empty(title)) getString(if (buffering) R.string.buffering else R.string.unknown_title) else title
            this.artist.text = if (Strings.empty(artist)) getString(if (buffering) R.string.buffering else R.string.unknown_artist) else artist
            this.album.text = if (Strings.empty(album)) getString(if (buffering) R.string.buffering else R.string.unknown_album) else album

            this.rebindAlbumArtistWithArtTextView(playback)
            this.titleWithArt.text = if (Strings.empty(title)) getString(if (buffering) R.string.buffering else R.string.unknown_title) else title
            this.buffering.visibility = if (buffering) View.VISIBLE else View.GONE

            val albumArtEnabledInSettings = this.prefs?.getBoolean(
                Prefs.Key.ALBUM_ART_ENABLED, Prefs.Default.ALBUM_ART_ENABLED) ?: false

            if (!albumArtEnabledInSettings || Strings.empty(artist) || Strings.empty(album)) {
                this.albumArtModel = AlbumArtModel.empty()
                setMetadataDisplayMode(DisplayMode.NoArtwork)
            }
            else {
                if (!this.albumArtModel.matches(artist, album)) {
                    this.albumArtModel.destroy()

                    this.albumArtModel = AlbumArtModel(
                        title, artist, album, AlbumArtModel.Size.Mega, albumArtRetrieved)
                }
                updateAlbumArt()
            }
        }
    }

    private val playbackService: PlaybackService
        get() = PlaybackServiceFactory.instance(context)

    private fun getString(resId: Int): String {
        return context.getString(resId)
    }

    private fun getString(resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }

    private fun setMetadataDisplayMode(mode: DisplayMode) {
        lastDisplayMode = mode

        if (mode == DisplayMode.Stopped) {
            albumArtImageView.setImageDrawable(null)
            mainTrackMetadataWithAlbumArt.visibility = View.GONE
            mainTrackMetadataNoAlbumArt.visibility = View.GONE
        }
        else if (mode == DisplayMode.Artwork) {
            mainTrackMetadataWithAlbumArt.visibility = View.VISIBLE
            mainTrackMetadataNoAlbumArt.visibility = View.GONE
        }
        else {
            albumArtImageView.setImageDrawable(null)
            mainTrackMetadataWithAlbumArt.visibility = View.GONE
            mainTrackMetadataNoAlbumArt.visibility = View.VISIBLE
        }
    }

    private fun rebindAlbumArtistWithArtTextView(playback: PlaybackService) {
        val buffering = playback.playbackState == PlaybackState.Buffering

        val artist = playback.getTrackString(
            Metadata.Track.ARTIST, getString(if (buffering) R.string.buffering else R.string.unknown_artist))

        val album = playback.getTrackString(
            Metadata.Track.ALBUM, getString(if (buffering) R.string.buffering else R.string.unknown_album))

        val albumColor = ForegroundColorSpan(getColorCompat(R.color.theme_orange))

        val artistColor = ForegroundColorSpan(getColorCompat(R.color.theme_yellow))

        val builder = SpannableStringBuilder().append(album).append(" - ").append(artist)

        val albumClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                navigateToCurrentAlbum()
            }

            override fun updateDrawState(ds: TextPaint) {}
        }

        val artistClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                navigateToCurrentArtist()
            }

            override fun updateDrawState(ds: TextPaint) {}
        }

        val artistOffset = album.length + 3

        builder.setSpan(albumColor, 0, album.length, 0)
        builder.setSpan(albumClickable, 0, album.length, 0)
        builder.setSpan(artistColor, artistOffset, artistOffset + artist.length, 0)
        builder.setSpan(artistClickable, artistOffset, artistOffset + artist.length, 0)

        artistAndAlbumWithArt.movementMethod = LinkMovementMethod.getInstance()
        artistAndAlbumWithArt.highlightColor = Color.TRANSPARENT
        artistAndAlbumWithArt.text = builder
    }

    private fun updateAlbumArt() {
        if (playbackService.playbackState == PlaybackState.Stopped) {
            setMetadataDisplayMode(DisplayMode.NoArtwork)
        }

        val url = albumArtModel.url

        if (Strings.empty(url)) {
            this.lastArtworkUrl = null
            albumArtModel.fetch()
            setMetadataDisplayMode(DisplayMode.NoArtwork)
        }
        else if (url != lastArtworkUrl || lastDisplayMode == DisplayMode.Stopped) {
            val loadId = albumArtModel.id
            this.lastArtworkUrl = url

            Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(object : RequestListener<String, GlideDrawable> {
                    override fun onException(
                            e: Exception?,
                            model: String?,
                            target: Target<GlideDrawable>?,
                            first: Boolean): Boolean
                    {
                        setMetadataDisplayMode(DisplayMode.NoArtwork)
                        lastArtworkUrl = null
                        return false
                    }

                    override fun onResourceReady(
                            resource: GlideDrawable, model: String, target: Target<GlideDrawable>,
                            memory: Boolean, first: Boolean): Boolean {
                        if (!isPaused) {
                            preloadNextImage()
                        }

                        /* if the loadId doesn't match the current id, then the image was
                        loaded for a different song. throw it away. */
                        if (albumArtModel.id != loadId) {
                            return true
                        }
                        else {
                            setMetadataDisplayMode(DisplayMode.Artwork)
                            return false
                        }
                    }
                })
                .into(albumArtImageView)
        }
        else {
            setMetadataDisplayMode(lastDisplayMode)
        }
    }

    private fun preloadNextImage() {
        val request = SocketMessage.Builder
            .request(Messages.Request.QueryPlayQueueTracks)
            .addOption(Messages.Key.OFFSET, playbackService.queuePosition + 1)
            .addOption(Messages.Key.LIMIT, 1)
            .build()

        this.wss.send(request, wssClient) { response: SocketMessage ->
            val data = response.getJsonArrayOption(Messages.Key.DATA, JSONArray())
            if (data != null && data.length() > 0) {
                val track = data.optJSONObject(0)
                val artist = track.optString(Metadata.Track.ARTIST, "")
                val album = track.optString(Metadata.Track.ALBUM, "")

                if (!albumArtModel.matches(artist, album)) {
                    AlbumArtModel("", artist, album, AlbumArtModel.Size.Mega) {
                                _: AlbumArtModel, url: String? ->
                            val width = albumArtImageView.width
                            val height = albumArtImageView.height
                            Glide.with(context).load(url).downloadOnly(width, height)
                        }
                    }
                }
            }
    }

    private fun init() {
        Application.mainComponent.inject(this)

        this.prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        val child = LayoutInflater.from(context).inflate(R.layout.main_metadata, this, false)

        addView(child, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        this.title = findViewById<TextView>(R.id.track_title)
        this.artist = findViewById<TextView>(R.id.track_artist)
        this.album = findViewById<TextView>(R.id.track_album)
        this.volume = findViewById<TextView>(R.id.volume)
        this.buffering = findViewById(R.id.buffering)

        this.titleWithArt = findViewById<TextView>(R.id.with_art_track_title)
        this.artistAndAlbumWithArt = findViewById<TextView>(R.id.with_art_artist_and_album)
        this.volumeWithArt = findViewById<TextView>(R.id.with_art_volume)

        this.mainTrackMetadataWithAlbumArt = findViewById(R.id.main_track_metadata_with_art)
        this.mainTrackMetadataNoAlbumArt = findViewById(R.id.main_track_metadata_without_art)
        this.albumArtImageView = findViewById<ImageView>(R.id.album_art)

        this.album.setOnClickListener { _ -> navigateToCurrentAlbum() }
        this.artist.setOnClickListener { _ -> navigateToCurrentArtist() }
    }

    private fun navigateToCurrentArtist() {
        val context = context
        val playback = playbackService

        val artistId = playback.getTrackLong(Metadata.Track.ARTIST_ID, -1)
        if (artistId != -1L) {
            val artistName = playback.getTrackString(Metadata.Track.ARTIST, "")
            context.startActivity(AlbumBrowseActivity.getStartIntent(
                context, Messages.Category.ARTIST, artistId, artistName))
        }
    }

    private fun navigateToCurrentAlbum() {
        val context = context
        val playback = playbackService

        val albumId = playback.getTrackLong(Metadata.Track.ALBUM_ID, -1)
        if (albumId != -1L) {
            val albumName = playback.getTrackString(Metadata.Track.ALBUM, "")
            context.startActivity(TrackListActivity.getStartIntent(
                context, Messages.Category.ALBUM, albumId, albumName))
        }
    }

    private val wssClient = object : WebSocketService.Client {
        override fun onStateChanged(newState: WebSocketService.State, oldState: WebSocketService.State) {}
        override fun onMessageReceived(message: SocketMessage) {}
        override fun onInvalidPassword() {}
    }

    private var albumArtRetrieved: (AlbumArtModel, String?) -> Unit = {
            model: AlbumArtModel, _: String? ->
        handler?.post {
            if (model === albumArtModel) {
                if (Strings.empty(model.url)) {
                    setMetadataDisplayMode(DisplayMode.NoArtwork)
                }
                else {
                    updateAlbumArt()
                }
            }
        }
    }
}
