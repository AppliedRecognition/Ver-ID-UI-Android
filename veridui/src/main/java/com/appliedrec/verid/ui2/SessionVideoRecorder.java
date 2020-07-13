package com.appliedrec.verid.ui2;

import android.media.MediaRecorder;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SessionVideoRecorder implements ISessionVideoRecorder, MediaRecorder.OnErrorListener {

    private final AtomicReference<MediaRecorder> mediaRecorderRef = new AtomicReference<>();
    private File videoFile;
    private final AtomicInteger width = new AtomicInteger(0);
    private final AtomicInteger height = new AtomicInteger(0);
    private final AtomicInteger rotation = new AtomicInteger(0);

    @Override
    public Optional<Surface> getSurface() {
        return getMediaRecorder(true).flatMap(mediaRecorder -> Optional.ofNullable(mediaRecorder.getSurface()));
    }

    @Override
    public void setup(Size videoSize, int rotationDegrees) {
        width.set(videoSize.getWidth());
        height.set(videoSize.getHeight());
        rotation.set(rotationDegrees);
    }

    @Override
    public void start() {
        getMediaRecorder(true).ifPresent(MediaRecorder::start);
    }

    @Override
    public void stop() {
        getMediaRecorder(false).ifPresent(mediaRecorder -> {
            mediaRecorderRef.set(null);
            try {
                mediaRecorder.stop();
            } catch (IllegalStateException e) {
                getVideoFile().ifPresent(File::delete);
                videoFile = null;
            }
            mediaRecorder.reset();
            mediaRecorder.release();
        });
    }

    @Override
    public Optional<File> getVideoFile() {
        return Optional.ofNullable(videoFile);
    }

    private MediaRecorder createMediaRecorder() throws Exception {
        getMediaRecorder(false).ifPresent(mediaRecorder -> {
            try {
                mediaRecorder.stop();
            } catch (Exception ignore) {
            }
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorderRef.set(null);
        });
        getVideoFile().ifPresent(File::delete);
        if (width.get() == 0 || height.get() == 0) {
            throw new Exception("Invalid video dimensions");
        }
        videoFile = File.createTempFile("video_", ".mp4");
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFile.getPath());
        mediaRecorder.setVideoEncodingBitRate(1000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(width.get(), height.get());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOrientationHint(rotation.get());
        mediaRecorder.setOnErrorListener(this);
        mediaRecorder.prepare();
        return mediaRecorder;
    }

    private Optional<MediaRecorder> getMediaRecorder(boolean createIfNotAvailable) {
        if (mediaRecorderRef.get() == null) {
            if (!createIfNotAvailable) {
                return Optional.empty();
            }
            try {
                MediaRecorder mediaRecorder = createMediaRecorder();
                mediaRecorderRef.set(mediaRecorder);
                return Optional.of(mediaRecorder);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.of(mediaRecorderRef.get());
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        stop();
    }

    @Override
    public void onError(MediaRecorder mediaRecorder, int i, int i1) {
        mediaRecorderRef.set(null);
        getVideoFile().ifPresent(File::delete);
    }
}
