package com.appliedrec.verid.ui2

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.appliedrec.verid.core2.EulerAngle
import java.util.*
import kotlin.math.*

@Composable
fun Head3DView(
    startAngle: EulerAngle,
    endAngle: EulerAngle,
    size: IntSize,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationDuration = Head3DViewHelper.getAnimationDuration(startAngle, endAngle)
    val currentState = remember { MutableTransitionState(startAngle) }
    currentState.targetState = endAngle
    val transition = updateTransition(currentState, label = "3D head transition")
    val angle by transition.animateValue(
        typeConverter = TwoWayConverter(
            convertToVector = { AnimationVector3D(it.yaw, it.pitch, it.roll) },
            convertFromVector = { vec: AnimationVector3D -> EulerAngle(vec.v1, vec.v2, vec.v3) }
        ),
        transitionSpec = {
            tween(animationDuration, easing = LinearOutSlowInEasing)
        },
        targetValueByState = { it },
        label = "3D head animation",
    )
    if (angle == endAngle) {
        onFinish()
    }
    val imageName = Head3DViewHelper.getHeadImageNameAtAngle(angle)
    // Check that image exists 1869.webp
    val bitmap = LocalContext.current.assets.open(imageName).use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    }
    Box(modifier = modifier
        .clip(shape = GenericShape { s, _ ->
            this.addOval(Rect(0f, 0f, s.width, s.height))
        })
        .background(Color.Gray)
    ) {
        val yOffset = with(LocalDensity.current) { (size.height.toFloat() * 0.1f).toDp() }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Face turning",
            modifier = Modifier.fillMaxSize().offset(y = yOffset),
            contentScale = ContentScale.Crop
        )
    }
}

object Head3DViewHelper {

    object HeadDimensions {
        const val COLS = 61
        const val ROWS = 30
    }

    fun getHeadImageNameAtAngle(angle: EulerAngle): String {
        val xOffset = floor(HeadDimensions.COLS.toDouble() / 2).toInt()
        val yOffset = floor(HeadDimensions.ROWS.toDouble() / 2).toInt()
        val x = clamp(angle.yaw.toInt() + xOffset, 0, HeadDimensions.COLS - 1)
        val y = clamp(angle.pitch.toInt() + yOffset, 0, HeadDimensions.ROWS - 1)
        val index = clamp(HeadDimensions.COLS * y + x, 0, HeadDimensions.COLS * HeadDimensions.ROWS - 1)
        return String.format(Locale.ROOT, "head_angles/%04d.webp", index)
    }

    private fun getDistanceBetweenAngles(startAngle: EulerAngle, endAngle: EulerAngle): Float {
        return hypot(
            (endAngle.yaw - startAngle.yaw).toDouble(),
            (endAngle.pitch - startAngle.pitch).toDouble()
        ).toFloat()
    }

    fun getAnimationDuration(startAngle: EulerAngle, endAngle: EulerAngle): Int {
        val frameCount = ceil(getDistanceBetweenAngles(startAngle, endAngle).toDouble()).toInt()
        return frameCount * 33
    }

    private fun <T: Number> clamp(value: T, min: T, max: T): T {
        val minValue = min.toDouble()
        val maxValue = max.toDouble()
        val valueToClamp = value.toDouble()
        val clampedValue = min(maxValue, max(minValue, valueToClamp))
        return when (value) {
            is Int -> clampedValue.toInt() as T
            is Long -> clampedValue.toLong() as T
            is Float -> clampedValue.toFloat() as T
            is Double -> clampedValue as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }
}