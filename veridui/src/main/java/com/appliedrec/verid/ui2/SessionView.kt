package com.appliedrec.verid.ui2

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.appliedrec.verid.core2.ImageUtils
import com.appliedrec.verid.core2.Size
import com.appliedrec.verid.core2.session.FaceDetectionResult
import com.appliedrec.verid.core2.session.FaceDetectionStatus
import com.appliedrec.verid.core2.session.VerIDSessionResult
import com.appliedrec.verid.ui2.ui.theme.VerIDTheme
import kotlinx.coroutines.*
import kotlin.math.*

class SessionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseSessionView(context, attrs) {

    private var faceDetectionResult: FaceDetectionResult? by mutableStateOf(null)
    private var prompt: String? by mutableStateOf(null)
    private var faceCaptureCount: Int by mutableStateOf(0)
    private var isFinishing: Boolean by mutableStateOf(false)
    private var latestMisalignTime: Long? = null
    private val textureView: TextureView
    private val ovalMaskView: OvalMaskView

    init {
        when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> {
                setBackgroundColor(Color.White.toArgb())
            } // Night mode is not active, we're using the light theme
            Configuration.UI_MODE_NIGHT_YES -> {
                setBackgroundColor(Color.Black.toArgb())
            } // Night mode is active, we're using dark theme
        }
        textureView = TextureView(context).apply {
            surfaceTextureListener = this@SessionView
            id = View.generateViewId()
        }
        ovalMaskView = OvalMaskView(context).apply {
            id = View.generateViewId()
        }
        val composeView = ComposeView(context).apply {
            setContent {
                VerIDTheme {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (faceDetectionResult != null) {
                            val faceOvalBounds = android.graphics.Rect()
                            faceDetectionResult!!.defaultFaceBounds.translatedToImageSize(viewSize)
                                .round(faceOvalBounds)
                            FaceDetectionResultView(
                                faceDetectionResult = faceDetectionResult!!,
                                isPreviewMirrored = isCameraPreviewMirrored,
                                isLastFaceCapture = faceCaptureCount >= sessionSettings.faceCaptureCount,
                                isFinishing = isFinishing,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .requiredSize(
                                        width = with(LocalDensity.current) {
                                            faceOvalBounds.width().toDp()
                                        },
                                        height = with(LocalDensity.current) {
                                            faceOvalBounds.height().toDp()
                                        }
                                    ))
                            if (prompt != null && faceCaptureCount < sessionSettings.faceCaptureCount) {
                                Text(
                                    text = prompt!!,
                                    style = typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(top = 16.dp)
                                )
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
        val textureViewLayoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(textureView, textureViewLayoutParams)
        val ovalMaskViewLayoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(ovalMaskView, ovalMaskViewLayoutParams)
        textureView.alpha = 0f
        ovalMaskView.visibility = View.GONE
        addView(composeView)
    }


    override fun remove() {
        Log.d("Ver-ID", "Remove what?")
    }

    override fun setFaceDetectionResult(
        faceDetectionResult: FaceDetectionResult?,
        prompt: String?
    ) {
        post {
            if (isFinishing) {
                return@post
            }
            if (faceCaptureCount >= sessionSettings.faceCaptureCount) {
                return@post
            }
            textureView.alpha = 1f
            if (faceDetectionResult?.status == FaceDetectionStatus.FACE_MISALIGNED) {
                if (latestMisalignTime == null) {
                    latestMisalignTime = System.currentTimeMillis()
                }
            } else {
                latestMisalignTime = null
            }
            faceDetectionResult?.let {
                val previewVisible = if (it.status == FaceDetectionStatus.FACE_ALIGNED) View.INVISIBLE else View.VISIBLE
                textureView.visibility = previewVisible
                ovalMaskView.visibility = previewVisible
                textureView.setTransform(getCameraViewMatrixFromFaceDetectionResult(it))
                maskCameraPreviewFromFaceDetectionResult(it)
                if (it.status == FaceDetectionStatus.FACE_ALIGNED) {
                    this.faceCaptureCount++
                }
            }
            this.faceDetectionResult = faceDetectionResult
            this.prompt = prompt
        }
    }

    override fun drawFaces(faceImages: MutableList<out Drawable>?) {

    }

    override fun getCapturedFaceImageHeight(): Int {
        return dpToPx(96)
    }

    override fun getViewSize(): Size {
        return Size(width, height)
    }

    override fun getTextureView(): TextureView {
        return textureView
    }

    override fun willFinishWithResult(result: VerIDSessionResult?, completionCallback: Runnable?) {
        isFinishing = true
        prompt = null
        completionCallback?.let { callback ->
            CoroutineScope(Dispatchers.Default).launch {
                delay(1000)
                withContext(Dispatchers.Main) {
                    textureView.alpha = 0f
                    ovalMaskView.visibility = View.GONE
                    faceDetectionResult = null
                    callback.run()
                }
            }
        }
    }

    override fun onSessionStarted() {
        super.onSessionStarted()
        isFinishing = false
        prompt = null
        ovalMaskView.visibility = View.VISIBLE
        faceCaptureCount = 0
    }

    private fun getDefaultFaceRectFromFaceDetectionResult(faceDetectionResult: FaceDetectionResult): RectF {
        return faceDetectionResult.defaultFaceBounds.translatedToImageSize(viewSize)
    }

    private fun getCameraViewMatrixFromFaceDetectionResult(faceDetectionResult: FaceDetectionResult): Matrix {
        when (faceDetectionResult.status) {
            FaceDetectionStatus.FACE_FIXED, FaceDetectionStatus.FACE_ALIGNED, FaceDetectionStatus.FACE_MISALIGNED -> {
                val faceBounds =
                    getFaceBoundsFromFaceDetectionResult(faceDetectionResult) ?: return matrix
                val faceRect = getDefaultFaceRectFromFaceDetectionResult(faceDetectionResult)
                val rectToRect = Matrix()
                rectToRect.setRectToRect(faceBounds, faceRect, Matrix.ScaleToFit.CENTER)
                val matrix = Matrix(cameraPreviewMatrix)
                matrix.postConcat(rectToRect)
                return matrix
            }
            else -> {
                return cameraPreviewMatrix
            }
        }
    }

    private fun getFaceBoundsFromFaceDetectionResult(faceDetectionResult: FaceDetectionResult): RectF? {
        if (!faceDetectionResult.faceBounds.isPresent) {
            return null
        }
        val imageSize = faceDetectionResult.imageSize
        val viewSize = viewSize
        val scale =
            max(viewSize.width / imageSize.width, viewSize.height / imageSize.height).toFloat()
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            viewSize.width.toFloat() / 2f - imageSize.width.toFloat() * scale / 2f,
            viewSize.height.toFloat() / 2f - imageSize.height.toFloat() * scale / 2f
        )
        val bounds = RectF(faceDetectionResult.faceBounds.get())
        matrix.mapRect(bounds)
        return bounds
    }

    private fun maskCameraPreviewFromFaceDetectionResult(faceDetectionResult: FaceDetectionResult) {
        val defaultFaceBounds = getDefaultFaceRectFromFaceDetectionResult(faceDetectionResult)
        when (faceDetectionResult.status) {
            FaceDetectionStatus.FACE_FIXED, FaceDetectionStatus.FACE_MISALIGNED, FaceDetectionStatus.FACE_ALIGNED -> maskCameraPreviewWithOvalInBounds(
                defaultFaceBounds
            )
            else -> {
                val bounds = getFaceBoundsFromFaceDetectionResult(faceDetectionResult)
                if (bounds != null) {
                    maskCameraPreviewWithOvalInBounds(bounds)
                } else {
                    maskCameraPreviewWithOvalInBounds(defaultFaceBounds)
                }
            }
        }
    }

    private fun maskCameraPreviewWithOvalInBounds(bounds: RectF) {
        ovalMaskView.setMaskRect(bounds)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FaceDetectionResultView(
    faceDetectionResult: FaceDetectionResult,
    isPreviewMirrored: Boolean,
    isLastFaceCapture: Boolean,
    isFinishing: Boolean,
    modifier: Modifier
) {
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var latestMisalignTime: Long? by remember { mutableStateOf(null) }
    if (faceDetectionResult.status != FaceDetectionStatus.FACE_MISALIGNED) {
        latestMisalignTime = null
    }
    when (faceDetectionResult.status) {
        FaceDetectionStatus.FACE_ALIGNED -> {
            var faceImage = ImageUtils.cropImageToFace(faceDetectionResult.image.provideBitmap(), faceDetectionResult.face.get())
            if (isPreviewMirrored) {
                val matrix = Matrix()
                matrix.setScale(-1f, 1f)
                faceImage = Bitmap.createBitmap(faceImage, 0, 0, faceImage.width, faceImage.height, matrix, false)
            }
            AnimatedVisibility(visible = !isFinishing, exit = scaleOut(), modifier = modifier) {
                Box(modifier = modifier
                    .clip(shape = GenericShape { size, _ ->
                        val width = size.width
                        val height = size.height
                        val x = (size.width - width) / 2f
                        val y = (size.height - height) / 2f
                        this.addOval(Rect(x, y, width, height))
                    })
                ) {
                    Image(
                        bitmap = faceImage.asImageBitmap(),
                        contentDescription = "Face",
                        contentScale = ContentScale.Crop,
                        modifier = modifier
                    )
                    val face = if (isPreviewMirrored) faceDetectionResult.face.get().flipped(Size(faceImage.width, faceImage.height)) else faceDetectionResult.face.get()
                    val faceBounds = face.bounds
                    val imageScale = max(parentSize.width.toFloat() / faceImage.width.toFloat(), parentSize.height.toFloat() / faceImage.height.toFloat())
                    val xOffset = 0f - faceBounds.left * imageScale
                    val yOffset = 0f - faceBounds.top * imageScale
                    val matrix = Matrix()
                    matrix.postScale(imageScale, imageScale)
                    matrix.postTranslate(xOffset - (parentSize.width.toFloat() / 2f - faceImage.width.toFloat() * imageScale / 2f), yOffset - (parentSize.height.toFloat() / 2f - faceImage.height.toFloat() * imageScale / 2f))
                    matrix.postTranslate(with(LocalDensity.current) { -3.dp.toPx() }, with(LocalDensity.current) { -3.dp.toPx() })
                    val faceLandmarks = face.landmarks.map { point ->
                        val vec = arrayOf(point.x, point.y).toFloatArray()
                        matrix.mapPoints(vec)
                        Offset(vec[0], vec[1])
                    }
                    var landmark by remember { mutableStateOf(faceLandmarks[17]) }
                    val landmarkSize = with(LocalDensity.current) { DpSize(6.dp, 6.dp).toSize() }
                    LaunchedEffect(key1 = isLastFaceCapture) {
                        var i = 18
                        while (i < faceLandmarks.size) {
                            delay(100)
                            landmark = faceLandmarks[i]
                            if (i == faceLandmarks.size - 1) {
                                i = 17
                            } else {
                                i++
                            }
                        }
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawOval(Color.White, topLeft = landmark, size = landmarkSize)
                    }
                }
            }
        }
        FaceDetectionStatus.FACE_FIXED -> {

        }
        FaceDetectionStatus.FACE_MISALIGNED -> {
            val now = System.currentTimeMillis()
            if (latestMisalignTime == null) {
                latestMisalignTime = now
            }
            if (latestMisalignTime!! < now - 2000) {
                val angles = faceDetectionResult.faceAngle.map { if (faceDetectionResult.requestedAngle.isPresent) Pair(it, faceDetectionResult.requestedAngle.get()) else null }.orElseGet { null }
                angles?.let {
                    Head3DView(
                        it.first,
                        it.second,
                        size = parentSize,
                        onFinish = {
                            latestMisalignTime = null
                        },
                        modifier = modifier
                    )
                }
            }
            DirectionArrow(
                faceDetectionResult = faceDetectionResult,
                parentSize = parentSize,
                modifier = modifier
                    .onSizeChanged {
                        parentSize = it
                    }
            )
        }
        else -> {
            if (faceDetectionResult.faceBounds.isPresent) {
                val transition = rememberInfiniteTransition()
                val strokeWidth by transition.animateFloat(
                    initialValue = 10f,
                    targetValue = 16f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val ovalColour = MaterialTheme.colorScheme.primary
                Canvas(modifier = modifier
                    .onSizeChanged {
                        parentSize = it
                    }, onDraw = {
                        drawOval(color = ovalColour, style = Stroke(width = strokeWidth))
                    }
                )
            }
        }
    }
}

@Composable
fun DirectionArrow(
    faceDetectionResult: FaceDetectionResult,
    parentSize: IntSize,
    modifier: Modifier
) {
    val offsetAngle = faceDetectionResult.offsetAngleFromBearing
    val arrowAngle = atan2(offsetAngle.pitch.toDouble(), offsetAngle.yaw.toDouble()).toFloat()
    val arrowDistance = hypot(offsetAngle.yaw.toDouble(), offsetAngle.pitch.toDouble()).toFloat() * 2f
    val arrowLength: Float = parentSize.width.toFloat() / 5f
    val stemLength = (arrowLength * arrowDistance)
        .coerceAtLeast(arrowLength * 0.75f)
        .coerceAtMost(arrowLength * 1.7f)
    val arrowTipAngle = Math.toRadians(40.0).toFloat()
    val arrowTipX: Float = parentSize.width.toFloat() / 2 + cos(arrowAngle.toDouble())
        .toFloat() * arrowLength / 2f
    val arrowTipY: Float = parentSize.height.toFloat() / 2 + sin(arrowAngle.toDouble())
        .toFloat() * arrowLength / 2f
    val arrowPoint1X = arrowTipX + cos(arrowAngle + PI - arrowTipAngle)
        .toFloat() * arrowLength * 0.6f
    val arrowPoint1Y = arrowTipY + sin(arrowAngle + PI - arrowTipAngle)
        .toFloat() * arrowLength * 0.6f
    val arrowPoint2X = arrowTipX + cos(arrowAngle + PI + arrowTipAngle)
        .toFloat() * arrowLength * 0.6f
    val arrowPoint2Y = arrowTipY + sin(arrowAngle + PI + arrowTipAngle)
        .toFloat() * arrowLength * 0.6f
    val arrowStartX = arrowTipX + cos(arrowAngle + PI).toFloat() * stemLength
    val arrowStartY = arrowTipY + sin(arrowAngle + PI).toFloat() * stemLength
    val arrowPath = Path().apply {
        moveTo(arrowPoint1X, arrowPoint1Y)
        lineTo(arrowTipX, arrowTipY)
        lineTo(arrowPoint2X, arrowPoint2Y)
        moveTo(arrowTipX, arrowTipY)
        lineTo(arrowStartX, arrowStartY)
    }
    Canvas(modifier = modifier) {
        val strokeWidth = size.width * 0.038f
        drawPath(
            arrowPath,
            color = Color.White,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}