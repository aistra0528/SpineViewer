package org.anonymous.spineviewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.esotericsoftware.spine.*
import com.esotericsoftware.spine.utils.TwoColorPolygonBatch
import java.lang.Math.clamp

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class Main(val platform: Platform) : ApplicationAdapter(), GestureListener {
    private lateinit var batch: TwoColorPolygonBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: ScreenViewport
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
        batch = TwoColorPolygonBatch()
        camera = OrthographicCamera()
        viewport = ScreenViewport()
        renderer = SkeletonRenderer()
    }

    override fun render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f)
        backgroundTexture?.let {
            batch.projectionMatrix.set(viewport.camera.combined)
            val vec = Vector4.Zero
            val scale = maxOf(
                Gdx.graphics.width / it.width.toFloat(), Gdx.graphics.height / it.height.toFloat()
            )
            vec.z = it.width * scale
            vec.w = it.height * scale
            vec.x = (Gdx.graphics.width - vec.z) / 2f
            vec.y = (Gdx.graphics.height - vec.w) / 2f
            batch.begin()
            batch.draw(it, vec.x, vec.y, vec.z, vec.w)
            batch.end()
        }
        skeleton?.let {
            camera.update()
            batch.projectionMatrix.set(camera.combined)
            state?.run {
                update(Gdx.graphics.deltaTime)
                apply(it)
            }
            it.updateWorldTransform()
            batch.begin()
            renderer.draw(batch, it)
            batch.end()
        }
    }

    override fun dispose() {
        batch.dispose()
        backgroundTexture?.dispose()
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        viewport.update(width, height, true)
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
                    camera.reset()
                    skeleton = Skeleton(it).apply {
                        animationIndex = 0
                        skinIndex = 0
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
                    skinIndex = (skinIndex + 1) % skins.count()
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
                animationIndex = (animationIndex + 1) % animations.count()
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
        camera.position.x -= deltaX * camera.zoom
        camera.position.y += deltaY * camera.zoom
        true
    } ?: false

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        skeleton?.run {
            val scale = clamp(lastScale * distance / initialDistance, 0.5f, 8f)
            camera.zoom = 1 / scale
        }
        return false
    }

    override fun pinch(
        initialPointer1: Vector2?, initialPointer2: Vector2?, pointer1: Vector2?, pointer2: Vector2?
    ): Boolean {
        return false
    }

    override fun pinchStop() {
        lastScale = 1 / camera.zoom
    }

    private fun OrthographicCamera.reset() {
        lastScale = 1f
        zoom = 1 / lastScale
        position.set(Vector3.Zero)
    }
}
