package scenes

import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val SPLASH_NAME = "Three Musketeers"
private const val SPLASH_HOLD_MS = 2000L
private const val SPLASH_GAP = 24

class SplashScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        loadPixelFont()
        val logoBitmap = resourcesVfs["logo_estudio.png"].readBitmap()

        solidRect(4960, 4540, Colors.BLACK).position(-2000, -2000)
        val content = container { alpha = 0.0 }
        val logo = content.image(logoBitmap)
        val name = content.pixelText(SPLASH_NAME, 33)
        val top = (views.virtualHeight - (logo.height + SPLASH_GAP + name.height)) / 2
        logo.position((views.virtualWidth - logo.width) / 2, top)
        name.position((views.virtualWidth - name.width.toInt()) / 2, (top + logo.height + SPLASH_GAP).toInt())

        views.launch {
            content.tween(content::alpha[1.0], time = 1.0.seconds)
            delay(SPLASH_HOLD_MS)
            content.tween(content::alpha[0.0], time = 1.0.seconds)
            val screen = views.actualVirtualBounds
            val black = views.stage.solidRect(screen.width, screen.height, Colors.BLACK) { position(screen.x, screen.y) }
            sceneContainer.changeTo { MenuScene() }
            black.tween(black::alpha[0.0], time = 1.0.seconds)
            black.removeFromParent()
        }
    }
}
