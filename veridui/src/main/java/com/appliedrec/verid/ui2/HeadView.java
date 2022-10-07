package com.appliedrec.verid.ui2;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.appliedrec.verid.core2.EulerAngle;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.Filament;
import com.google.android.filament.LightManager;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.SwapChain;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.ModelViewer;
import com.google.ar.sceneform.CameraNode;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.SceneView;
import io.github.sceneview.node.LightNode;
import io.github.sceneview.node.ModelNode;

public class HeadView extends SceneView {

//    private Choreographer choreographer;
//    private DisplayHelper displayHelper;
//    private UiHelper uiHelper;
//    private Engine engine;
//    private Renderer renderer;
//    private SwapChain swapChain;
//    private Scene scene;
//    private View view;
//    private Camera camera;
//    private ModelViewer modelViewer;
//    private final SurfaceView surfaceView;
//    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
//        @Override
//        public void doFrame(long frameTimeNanos) {
//            choreographer.postFrameCallback(this);
//            if (uiHelper.isReadyToRender() && renderer.beginFrame()) {
//
//            }
//        }
//    };

    private final ModelNode headNode;
    private boolean isAnimating = false;

    public HeadView(Context context) {
        this(context, null);
    }

    public HeadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        headNode = new ModelNode();
        headNode.loadModelAsync(context, null, "head1.obj", false, null, null, (error) -> {
            return null;
        }, (asset) -> {
            return null;
        });
        headNode.setModelPosition(new Float3(0, 0, -10));
        addChild(headNode);
        CameraNode cameraNode = new CameraNode();
        cameraNode.setVerticalFovDegrees(30);
        cameraNode.lookAt(headNode, new Float3(0,0,0), false);
        addChild(cameraNode);
        LightNode lightNode = new LightNode(new Float3(0, 10, 10), new Float3(), new Float3());
        addChild(lightNode);
//        Filament.init();
//        surfaceView = new SurfaceView(getContext());
//        addView(surfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

//    @Override
//    protected void onAttachedToWindow() {
//        super.onAttachedToWindow();
//        choreographer = Choreographer.getInstance();
//        displayHelper = new DisplayHelper(getContext());
//        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
//        uiHelper.setRenderCallback(new UiHelper.RendererCallback() {
//            @Override
//            public void onNativeWindowChanged(Surface surface) {
//                if (swapChain != null) {
//                    engine.destroySwapChain(swapChain);
//                }
//                swapChain = engine.createSwapChain(surface);
//                displayHelper.attach(renderer, surfaceView.getDisplay());
//            }
//
//            @Override
//            public void onDetachedFromSurface() {
//                displayHelper.detach();
//                if (swapChain != null) {
//                    engine.destroySwapChain(swapChain);
//                    engine.flushAndWait();
//                    swapChain = null;
//                }
//            }
//
//            @Override
//            public void onResized(int width, int height) {
//                double aspect = (double) width / (double) height;
//                camera.setProjection(30.0, aspect, 0.1, 20.0, Camera.Fov.VERTICAL);
//                view.setViewport(new Viewport(0, 0, width, height));
//            }
//        });
//        uiHelper.attachTo(surfaceView);
//        engine = Engine.create();
//        renderer = engine.createRenderer();
//        scene = engine.createScene();
//        view = engine.createView();
//        camera = engine.createCamera(engine.getEntityManager().create());
//        view.setCamera(camera);
//        view.setScene(scene);
//        choreographer.postFrameCallback(frameCallback);
//    }
//
//    @Override
//    protected void onDetachedFromWindow() {
//        super.onDetachedFromWindow();
//        uiHelper.detach();
//        uiHelper.setRenderCallback(null);
//        choreographer.removeFrameCallback(frameCallback);
//
////        animator.cancel();
//
////        // Cleanup all resources
////        engine.destroyEntity(light);
////        engine.destroyEntity(renderable);
////        engine.destroyRenderer(renderer);
////        engine.destroyVertexBuffer(vertexBuffer);
////        engine.destroyIndexBuffer(indexBuffer);
////        engine.destroyMaterialInstance(materialInstance);
////        engine.destroyMaterial(material);
//        engine.destroyView(view);
//        engine.destroyScene(scene);
//        engine.destroyCameraComponent(camera.getEntity());
//
//        EntityManager entityManager = EntityManager.get();
////        entityManager.destroy(light);
////        entityManager.destroy(renderable);
//        entityManager.destroy(camera.getEntity());
//
//        // Destroying the engine will free up any resource you may have forgotten
//        // to destroy, but it's recommended to do the cleanup properly
//        engine.destroy();
//
//        displayHelper = null;
//        uiHelper = null;
//        choreographer = null;
//        engine = null;
//        camera = null;
//        view = null;
//        scene = null;
//    }

    public void animateFromAngleToAngle(EulerAngle fromAngle, EulerAngle toAngle, long duration, Runnable callback) {
        if (isAnimating) {
            callback.run();
            return;
        }
        headNode.setRotation(new Float3(fromAngle.getPitch(), fromAngle.getYaw(), 0));
        ValueAnimator angleAnimator = ValueAnimator.ofObject(new EulerAngleEvaluator(), fromAngle, toAngle);
        angleAnimator.setDuration(duration);
        angleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        angleAnimator.addUpdateListener(animation -> {
            headNode.setRotation(new Float3(fromAngle.getPitch(), fromAngle.getYaw(), 0));
        });
        angleAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                isAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        angleAnimator.start();
    }
}
