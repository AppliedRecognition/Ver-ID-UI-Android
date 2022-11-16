package com.appliedrec.verid.ui2

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.appliedrec.verid.core2.EulerAngle
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode

class HeadViewSetup {

    companion object {
        @JvmStatic
        fun createHeadView(context: Context, lifecycleOwner: LifecycleOwner): SceneView {
            val sceneView = SceneView(context).apply {
                setLifecycle(lifecycleOwner.lifecycle)
            }
            lifecycleOwner.lifecycleScope.launchWhenCreated {
        //            val hdrFile = "environments/studio_small_09_2k.hdr"
        //            sceneView.loadHdrIndirectLight(hdrFile, specularFilter = true) {
        //                intensity(30_000f)
        //            }
        //            sceneView.loadHdrSkybox(hdrFile) {
        //                intensity(50_000f)
        //            }

                val model = sceneView.modelLoader.loadModel("head1.glb")!!
                val modelNode = ModelNode(sceneView, model).apply {
                    transform(
                        position = Position(z = -4.0f),
                        rotation = Rotation(x = 15.0f)
                    )
                    scaleToUnitsCube(2.0f)
                    // TODO: Fix centerOrigin
                    //                centerOrigin(Position(x=-1.0f, y=-1.0f))
                }
                sceneView.addChildNode(modelNode)
            }
            return sceneView
        }

        val runningAnimations = mutableSetOf<ModelNode>()

        @JvmStatic
        fun animateHead(sceneView: SceneView, fromAngle: EulerAngle, toAngle: EulerAngle, duration: Long, completion: Runnable) {
            val headNode: ModelNode = sceneView.childNodes.find {
                it is ModelNode
            } as ModelNode
            if (runningAnimations.contains(headNode)) {
                completion.run()
                return
            }
            headNode.rotation = Float3(fromAngle.pitch, fromAngle.yaw, 0F)
            val angleAnimator = ValueAnimator.ofObject(EulerAngleEvaluator(), fromAngle, toAngle)
            angleAnimator.duration = duration
            angleAnimator.interpolator = AccelerateDecelerateInterpolator()
            angleAnimator.addUpdateListener { animation: ValueAnimator? ->
                headNode.rotation = Float3(fromAngle.pitch, fromAngle.yaw, 0F)
            }
            angleAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    runningAnimations.add(headNode)
                }

                override fun onAnimationEnd(animation: Animator) {
                    runningAnimations.remove(headNode)
                    completion.run()
                }

                override fun onAnimationCancel(animation: Animator) {
                    runningAnimations.remove(headNode)
                    completion.run()
                }

                override fun onAnimationRepeat(animation: Animator) {}
            })
            angleAnimator.start()
        }
    }
}