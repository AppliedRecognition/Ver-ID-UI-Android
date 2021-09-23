package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

public class SessionVideoFragment extends Fragment implements TextureView.SurfaceTextureListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnVideoSizeChangedListener, SurfaceHolder.Callback {

    private static final String ARG_VIDEO_URI = "video_uri";
    private MediaPlayer mediaPlayer;
    private final Object mediaPlayerLock = new Object();
    private SurfaceHolder surfaceHolder;
    private TextureView textureView;

    public static SessionVideoFragment newInstance(Uri videoUri) {

        Bundle args = new Bundle();
        args.putParcelable(ARG_VIDEO_URI, videoUri);

        SessionVideoFragment fragment = new SessionVideoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_session_video, container, false);
//        SurfaceView surfaceView = view.findViewById(R.id.surfaceView);
//        surfaceHolder = surfaceView.getHolder();
//        surfaceHolder.addCallback(this);
        textureView = view.findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);
        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) {
            Uri videoUri = args.getParcelable(ARG_VIDEO_URI);
            if (videoUri != null) {
                synchronized (mediaPlayerLock) {
                    try {
                        mediaPlayer = MediaPlayer.create(context, videoUri);
                        mediaPlayer.setOnPreparedListener(this);
                        mediaPlayer.setOnCompletionListener(this);
                        mediaPlayer.setOnVideoSizeChangedListener(this);
                    } catch (Throwable ignored) {
                        mediaPlayer = null;
                    }
                }
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        synchronized (mediaPlayerLock) {
            if (mediaPlayer != null) {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        synchronized (mediaPlayerLock) {
            if (mediaPlayer != null) {
                mediaPlayer.setSurface(new Surface(surfaceTexture));
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int w, int h) {
//        if (surfaceHolder != null) {
//            surfaceHolder.setFixedSize(w, h);
//        }
        ConstraintLayout parentView = (ConstraintLayout) textureView.getParent();
        float viewAspectRatio = (float)parentView.getWidth()/(float)parentView.getHeight();
        float videoAspectRatio = (float)w/(float)h;
        float scale;
        if (videoAspectRatio > viewAspectRatio) {
            scale = (float)parentView.getHeight() / (float)h;
        } else {
            scale = (float)parentView.getWidth() / (float)w;
        }
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(textureView.getLayoutParams());
        layoutParams.width = (int)((float)w * scale);
        layoutParams.height = (int)((float)h * scale);
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        textureView.setLayoutParams(layoutParams);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        synchronized (mediaPlayerLock) {
            if (mediaPlayer != null) {
                mediaPlayer.setDisplay(surfaceHolder);
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
