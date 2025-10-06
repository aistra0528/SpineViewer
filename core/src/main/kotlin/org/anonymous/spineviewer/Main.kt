package org.anonymous.spineviewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.ScreenUtils
import com.esotericsoftware.spine.*
import java.lang.Math.clamp

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class Main(val platform: Platform) : ApplicationAdapter(), GestureListener {
    private lateinit var batch: PolygonSpriteBatch
    private lateinit var renderer: SkeletonRenderer
    private var skeleton: Skeleton? = null
    private var skeletonData: SkeletonData? = null
    private var state: AnimationState? = null
    private var animationIndex = 0
    private var skinIndex = 0
    private var lastScale = 1f
    private var currentFile = ""
    private var backgroundTexture: Texture? = null
    private var currentBackground: String? = null

    override fun create() {
        Gdx.input.inputProcessor = GestureDetector(this)
        batch = PolygonSpriteBatch()
        renderer = SkeletonRenderer()
    }

    override fun render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f)
        val vec = Vector4.Zero
        backgroundTexture?.run {
            val scale = maxOf(
                Gdx.graphics.width / width.toFloat(), Gdx.graphics.height / height.toFloat()
            )
            vec.z = width * scale
            vec.w = height * scale
            vec.x = (Gdx.graphics.width - vec.z) / 2f
            vec.y = (Gdx.graphics.height - vec.w) / 2f
        }
        skeleton?.let {
            state?.run {
                update(Gdx.graphics.deltaTime)
                apply(it)
            }
            it.updateWorldTransform()
            batch.begin()
            backgroundTexture?.let { bg -> batch.draw(bg, vec.x, vec.y, vec.z, vec.w) }
            renderer.draw(batch, it)
            batch.end()
        } ?: backgroundTexture?.let {
            batch.begin()
            batch.draw(it, vec.x, vec.y, vec.z, vec.w)
            batch.end()
        }
    }

    override fun dispose() {
        batch.dispose()
        backgroundTexture?.dispose()
    }

    override fun resume() {
        super.resume()
        if (currentFile != platform.getCurrentFile()) {
            currentFile = platform.getCurrentFile()
            TextureAtlas(Gdx.files.external("${currentFile.dropLast(5)}.atlas")).let { atlas ->
                val handle = Gdx.files.external(currentFile)
                skeletonData = if (currentFile.endsWith(".skel")) SkeletonBinary(atlas).readSkeletonData(handle)
                else SkeletonJson(atlas).readSkeletonData(handle)
                skeletonData?.let {
                    skeleton = Skeleton(it).apply {
                        reset()
                        setSkin(it.skins[skinIndex])
                        setSlotsToSetupPose()
                    }
                    state = AnimationState(AnimationStateData(it)).apply {
                        setAnimation(0, it.animations[animationIndex], true)
                    }
                }
            }
        }
        if (currentBackground != platform.getCurrentBackground()) {
            currentBackground = platform.getCurrentBackground()
            backgroundTexture = currentBackground?.let {
                Texture(Gdx.files.external(it))
            }
        }
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (currentFile.isNotEmpty() && x > 128) {
            skeletonData?.run {
                if (skins.count() > 1) {
                    skinIndex += 1
                    if (skinIndex >= skins.count()) skinIndex = 0
                    skeleton?.run {
                        setSkin(skins[skinIndex])
                        setSlotsToSetupPose()
                        return true
                    }
                }
            }
            return false
        }
        platform.importFiles()
        return true
    }

    override fun longPress(x: Float, y: Float): Boolean {
        skeletonData?.run {
            if (animations.count() > 1) {
                animationIndex += 1
                if (animationIndex >= animations.count()) animationIndex = 0
                state?.run {
                    setAnimation(0, animations[animationIndex], true)
                    return true
                }
            }
        }
        return false
    }

    override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean {
        return false
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean = skeleton?.let {
        it.setPosition(it.x + deltaX, it.y - deltaY)
        true
    } ?: false

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        skeleton?.run {
            val scale = clamp(lastScale * distance / initialDistance, 0.5f, 8f)
            setScale(scale, scale)
        }
        return false
    }

    override fun pinch(
        initialPointer1: Vector2?, initialPointer2: Vector2?, pointer1: Vector2?, pointer2: Vector2?
    ): Boolean {
        return false
    }

    override fun pinchStop() {
        skeleton?.run {
            lastScale = scaleX
        }
    }

    private fun Skeleton.reset() {
        setPosition(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f)
        lastScale = 1f
        setScale(lastScale, lastScale)
        animationIndex = 0
        skinIndex = 0
    }
}
