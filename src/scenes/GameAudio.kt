package scenes

import korlibs.audio.sound.*
import korlibs.event.*
import korlibs.io.file.std.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*


enum class Sfx(val file: String, val volume: Double) {
    CLICK("sfx/click.wav", 0.35),
    CARD_SELECT("sfx/card_select.wav", 0.4),
    CARD_DESELECT("sfx/card_deselect.wav", 0.35),
    ERROR("sfx/error.wav", 0.35),
    DISCARD("sfx/discard.wav", 0.8),
    DRAW("sfx/draw.wav", 0.8),
    SHUFFLE("sfx/shuffle.wav", 0.8),
    FIGHT("sfx/fight.wav", 0.8),
    WIN("sfx/win.wav", 0.55),
    BOSS_APPEARS("sfx/boss_appears.wav", 0.8),
    BOSS_DEFEATED("sfx/boss_defeated.wav", 0.7),
    VICTORY("sfx/victory.wav", 0.8),
    LOSE("sfx/lose.wav", 0.55)
}

enum class MusicTrack(val file: String) {
    MENU("music/menu.wav"),
    DUEL("music/duel.wav"),
    BOSS("music/boss.wav"),
    SHOP("music/shop.wav"),
    GAME_OVER("music/gameover.wav")
}

/**
 * Todo el sonido del juego. Funciona sin internet: los archivos van dentro de la app.
 * Si un archivo falta o no se puede leer, el juego sigue funcionando sin ese sonido.
 */
object GameAudio {
    private const val MUTED_KEY = "audioMuted"
    private const val MUSIC_VOLUME = 0.35

    private var views: Views? = null
    private val sounds = mutableMapOf<Sfx, Sound>()
    private val music = mutableMapOf<MusicTrack, Sound>()
    private var musicChannel: SoundChannel? = null
    private var currentTrack: MusicTrack? = null

    /** true si el jugador apagó el sonido. Queda guardado en el dispositivo. */
    var muted = false
        private set

    /** Carga todos los sonidos una sola vez. Se puede llamar desde cualquier escena: las siguientes veces no hace nada. */
    suspend fun load(views: Views) {
        if (this.views != null) return
        this.views = views
        muted = views.storage.getOrNull(MUTED_KEY) == "1"

        views.gameWindow.onEvent(PauseEvent) {
            musicChannel?.stop()
            musicChannel = null
        }
        views.gameWindow.onEvent(ResumeEvent) { startMusic() }
        for (sfx in Sfx.entries) {
            try {
                sounds[sfx] = resourcesVfs[sfx.file].readSound()
            } catch (e: Throwable) {
                println("Sonido no cargado: ${sfx.file} ($e)")
            }
        }
        for (track in MusicTrack.entries) {
            try {
                music[track] = resourcesVfs[track.file].readMusic()
            } catch (e: Throwable) {
                println("Música no cargada: ${track.file} ($e)")
            }
        }
    }

    /** Reproduce un efecto. No hace nada si el sonido está apagado o el archivo no cargó. */
    fun play(sfx: Sfx) {
        if (muted) return
        val v = views ?: return
        val sound = sounds[sfx] ?: return
        sound.play(v.coroutineContext, PlaybackParameters(volume = sfx.volume))
    }

    /** Cambia la música de fondo. Si esa pista ya está sonando, la deja seguir sin reiniciarla. */
    fun playMusic(track: MusicTrack) {
        if (currentTrack == track && musicChannel != null) return
        currentTrack = track
        startMusic()
    }

    /** Detiene la música (por ejemplo, al perder o al ganar la run, para que se oiga el efecto). */
    fun stopMusic() {
        currentTrack = null
        musicChannel?.stop()
        musicChannel = null
    }

    /** Activa o desactiva todo el sonido y lo guarda en el dispositivo. */
    fun toggleMute() {
        muted = !muted
        views?.storage?.set(MUTED_KEY, if (muted) "1" else "0")
        if (muted) {
            musicChannel?.stop()
            musicChannel = null
        } else {
            startMusic()
        }
    }

    private fun startMusic() {
        musicChannel?.stop()
        musicChannel = null
        if (muted) return
        val v = views ?: return
        val sound = music[currentTrack ?: return] ?: return

        musicChannel = sound.play(
            v.coroutineContext,
            PlaybackParameters(times = infinitePlaybackTimes, volume = MUSIC_VOLUME)
        )
    }
}
