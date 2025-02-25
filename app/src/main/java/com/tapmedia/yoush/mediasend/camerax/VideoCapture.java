/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tapmedia.yoush.mediasend.camerax;

/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraXThreads;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.DeferrableSurface;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.ImmediateSurface;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.VideoCaptureConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.internal.utils.UseCaseConfigUtil;

import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.video.VideoUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A use case for taking a video.
 *
 * <p>This class is designed for simple video capturing. It gives basic configuration of the
 * recorded video such as resolution and file format.
 *
 * @hide In the earlier stage, the VideoCapture is deprioritized.
 */
// Begin Signal Custom Code Block
@RequiresApi(26)
// End Signal Custom Code Block
@RestrictTo(Scope.LIBRARY_GROUP)
@SuppressWarnings("ClassCanBeStatic") // TODO(b/141958189): Suppressed during upgrade to AGP 3.6.
public class VideoCapture extends UseCase {

    /**
     * An unknown error occurred.
     *
     * <p>See message parameter in onError callback or log for more details.
     */
    public static final int ERROR_UNKNOWN = 0;
    /**
     * An error occurred with encoder state, either when trying to change state or when an
     * unexpected state change occurred.
     */
    public static final int ERROR_ENCODER = 1;
    /** An error with muxer state such as during creation or when stopping. */
    public static final int ERROR_MUXER = 2;
    /**
     * An error indicating start recording was called when video recording is still in progress.
     */
    public static final int ERROR_RECORDING_IN_PROGRESS = 3;

    /**
     * Provides a static configuration with implementation-agnostic options.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final Defaults DEFAULT_CONFIG = new Defaults();
    private static final Metadata EMPTY_METADATA = new Metadata();
    private static final String TAG = "VideoCapture";
    /** Amount of time to wait for dequeuing a buffer from the videoEncoder. */
    private static final int DEQUE_TIMEOUT_USEC = 10000;
    /** Android preferred mime type for AVC video. */
    // Begin Signal Custom Code Block
    private static final String VIDEO_MIME_TYPE = VideoUtil.VIDEO_MIME_TYPE;
    private static final String AUDIO_MIME_TYPE = VideoUtil.AUDIO_MIME_TYPE;
    // End Signal Custom Code Block
    /** Camcorder profiles quality list */
    private static final int[] CamcorderQuality = {
        CamcorderProfile.QUALITY_2160P,
        CamcorderProfile.QUALITY_1080P,
        CamcorderProfile.QUALITY_720P,
        CamcorderProfile.QUALITY_480P
    };
    /**
     * Audio encoding
     *
     * <p>the result of PCM_8BIT and PCM_FLOAT are not good. Set PCM_16BIT as the first option.
     */
    private static final short[] sAudioEncoding = {
        AudioFormat.ENCODING_PCM_16BIT,
        AudioFormat.ENCODING_PCM_8BIT,
        AudioFormat.ENCODING_PCM_FLOAT
    };
    private final BufferInfo mVideoBufferInfo = new BufferInfo();
    private final Object mMuxerLock = new Object();
    /** Thread on which all encoding occurs. */
    private final HandlerThread mVideoHandlerThread =
        new HandlerThread(CameraXThreads.TAG + "video encoding thread");
    private final Handler mVideoHandler;
    /** Thread on which audio encoding occurs. */
    private final HandlerThread mAudioHandlerThread =
        new HandlerThread(CameraXThreads.TAG + "audio encoding thread");
    private final Handler mAudioHandler;
    private final AtomicBoolean mEndOfVideoStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioVideoSignal = new AtomicBoolean(true);
    private final BufferInfo mAudioBufferInfo = new BufferInfo();
    /** For record the first sample written time. */
    private final AtomicBoolean mIsFirstVideoSampleWrite = new AtomicBoolean(false);
    private final AtomicBoolean mIsFirstAudioSampleWrite = new AtomicBoolean(false);

    @NonNull
    MediaCodec mVideoEncoder;
    @NonNull
    private MediaCodec mAudioEncoder;
    /** The muxer that writes the encoding data to file. */
    @GuardedBy("mMuxerLock")
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    /** The index of the video track used by the muxer. */
    private int mVideoTrackIndex;
    /** The index of the audio track used by the muxer. */
    private int mAudioTrackIndex;
    /** Surface the camera writes to, which the videoEncoder uses as input. */
    Surface mCameraSurface;

    /** audio raw data */
    @NonNull
    private AudioRecord mAudioRecorder;
    private int mAudioBufferSize;
    private boolean mIsRecording = false;
    private int mAudioChannelCount;
    private int mAudioSampleRate;
    private int mAudioBitRate;
    private DeferrableSurface mDeferrableSurface;

    /**
     * Creates a new video capture use case from the given configuration.
     *
     * @param config for this use case instance
     */
    public VideoCapture(VideoCaptureConfig config) {
        super(config);

        // video thread start
        mVideoHandlerThread.start();
        mVideoHandler = new Handler(mVideoHandlerThread.getLooper());

        // audio thread start
        mAudioHandlerThread.start();
        mAudioHandler = new Handler(mAudioHandlerThread.getLooper());
    }

    /** Creates a {@link MediaFormat} using parameters from the configuration */
    private static MediaFormat createMediaFormat(VideoCaptureConfig config, Size resolution) {
        MediaFormat format =
            MediaFormat.createVideoFormat(
                VIDEO_MIME_TYPE, resolution.getWidth(), resolution.getHeight());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.getBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.getVideoFrameRate());
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.getIFrameInterval());

        return format;
    }


    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @Nullable
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder(@Nullable CameraInfo cameraInfo) {
        VideoCaptureConfig defaults = CameraX.getDefaultUseCaseConfig(VideoCaptureConfig.class,
            cameraInfo);
        if (defaults != null) {
            return VideoCaptureConfig.Builder.fromConfig(defaults);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected Map<String, Size> onSuggestedResolutionUpdated(
        @NonNull Map<String, Size> suggestedResolutionMap) {
        if (mCameraSurface != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mAudioEncoder.stop();
            mAudioEncoder.release();
            releaseCameraSurface(false);
        }

        try {
            mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create MediaCodec due to: " + e.getCause());
        }

        String cameraId = getBoundCameraId();
        Size resolution = suggestedResolutionMap.get(cameraId);
        if (resolution == null) {
            throw new IllegalArgumentException(
                "Suggested resolution map missing resolution for camera " + cameraId);
        }

        setupEncoder(cameraId, resolution);
        return suggestedResolutionMap;
    }

    /**
     * Starts recording video, which continues until {@link VideoCapture#stopRecording()} is
     * called.
     *
     * <p>StartRecording() is asynchronous. User needs to check if any error occurs by setting the
     * {@link OnVideoSavedCallback#onError(int, String, Throwable)}.
     *
     * @param saveLocation Location to save the video capture
     * @param executor     The executor in which the callback methods will be run.
     * @param callback     Callback for when the recorded video saving completion or failure.
     */
    // Begin Signal Custom Code Block
    public void startRecording(@NonNull FileDescriptor saveLocation,
    // End Signal Custom Code Block
                               @NonNull Executor executor, @NonNull OnVideoSavedCallback callback) {
        mIsFirstVideoSampleWrite.set(false);
        mIsFirstAudioSampleWrite.set(false);
        startRecording(saveLocation, EMPTY_METADATA, executor, callback);
    }

    /**
     * Starts recording video, which continues until {@link VideoCapture#stopRecording()} is
     * called.
     *
     * <p>StartRecording() is asynchronous. User needs to check if any error occurs by setting the
     * {@link OnVideoSavedCallback#onError(int, String, Throwable)}.
     *
     * @param saveLocation Location to save the video capture
     * @param metadata     Metadata to save with the recorded video
     * @param executor     The executor in which the callback methods will be run.
     * @param callback     Callback for when the recorded video saving completion or failure.
     */
    public void startRecording(
        // Begin Signal Custom Code Block
        @NonNull FileDescriptor saveLocation,
        // End Signal Custom Code Block
        @NonNull Metadata metadata,
        @NonNull Executor executor,
        @NonNull OnVideoSavedCallback callback) {
        Log.i(TAG, "startRecording");
        OnVideoSavedCallback postListener = new VideoSavedListenerWrapper(executor, callback);

        if (!mEndOfAudioVideoSignal.get()) {
            postListener.onError(
                ERROR_RECORDING_IN_PROGRESS, "It is still in video recording!",
                null);
            return;
        }

        try {
            // audioRecord start
            // Begin Signal Custom Code Block
            if (mAudioRecorder != null) {
                mAudioRecorder.startRecording();
            } else {
                Log.w(TAG, "Missing audio recorder in start()!");
            }
            // End Signal Custom Code Block
        } catch (IllegalStateException e) {
            postListener.onError(ERROR_ENCODER, "AudioRecorder start fail", e);
            return;
        }

        CameraInternal boundCamera = getBoundCamera();
        String cameraId = getBoundCameraId();
        Size resolution = getAttachedSurfaceResolution(cameraId);
        try {
            // video encoder start
            Log.i(TAG, "videoEncoder start");
            mVideoEncoder.start();
            // audio encoder start
            Log.i(TAG, "audioEncoder start");
            mAudioEncoder.start();

        } catch (IllegalStateException e) {
            setupEncoder(cameraId, resolution);
            postListener.onError(ERROR_ENCODER, "Audio/Video encoder start fail",
                e);
            return;
        }

        CameraInfoInternal cameraInfoInternal = boundCamera.getCameraInfoInternal();
        int relativeRotation = cameraInfoInternal.getSensorRotationDegrees(
            ((ImageOutputConfig) getUseCaseConfig()).getTargetRotation(Surface.ROTATION_0));

        try {
            synchronized (mMuxerLock) {
                mMuxer =
                    new MediaMuxer(
                        // Begin Signal Custom Code Block
                        saveLocation,
                        // End Signal Custom Code Block
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                mMuxer.setOrientationHint(relativeRotation);
                if (metadata.location != null) {
                    mMuxer.setLocation(
                        (float) metadata.location.getLatitude(),
                        (float) metadata.location.getLongitude());
                }
            }
        } catch (IOException e) {
            setupEncoder(cameraId, resolution);
            postListener.onError(ERROR_MUXER, "MediaMuxer creation failed!", e);
            return;
        }

        mEndOfVideoStreamSignal.set(false);
        mEndOfAudioStreamSignal.set(false);
        mEndOfAudioVideoSignal.set(false);
        mIsRecording = true;

        notifyActive();
        mAudioHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    VideoCapture.this.audioEncode(postListener);
                }
            });

        mVideoHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    boolean errorOccurred = VideoCapture.this.videoEncode(postListener,
                        cameraId, resolution);
                    if (!errorOccurred) {
                        postListener.onVideoSaved(saveLocation);
                    }
                }
            });
    }

    /**
     * Stops recording video, this must be called after {@link
     * VideoCapture#startRecording(File, Metadata, Executor, OnVideoSavedCallback)} is called.
     *
     * <p>stopRecording() is asynchronous API. User need to check if {@link
     * OnVideoSavedCallback#onVideoSaved(File)} or
     * {@link OnVideoSavedCallback#onError(int, String, Throwable)} be called
     * before startRecording.
     */
    public void stopRecording() {
        Log.i(TAG, "stopRecording");
        notifyInactive();
        if (!mEndOfAudioVideoSignal.get() && mIsRecording) {
            // stop audio encoder thread, and wait video encoder and muxer stop.
            mEndOfAudioStreamSignal.set(true);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void clear() {
        mVideoHandlerThread.quitSafely();

        // audio encoder release
        mAudioHandlerThread.quitSafely();
        if (mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mAudioRecorder != null) {
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        if (mCameraSurface != null) {
            releaseCameraSurface(true);
        }

        super.clear();
    }

    private void releaseCameraSurface(final boolean releaseVideoEncoder) {
        if (mDeferrableSurface == null) {
            return;
        }

        final MediaCodec videoEncoder = mVideoEncoder;

        // Calling close should allow termination future to complete and close the surface with
        // the listener that was added after constructing the DeferrableSurface.
        mDeferrableSurface.close();
        mDeferrableSurface.getTerminationFuture().addListener(
            () -> {
                if (releaseVideoEncoder && videoEncoder != null) {
                    videoEncoder.release();
                }
            }, CameraXExecutors.mainThreadExecutor());

        if (releaseVideoEncoder) {
            mVideoEncoder = null;
        }
        mCameraSurface = null;
        mDeferrableSurface = null;
    }


    /**
     * Sets the desired rotation of the output video.
     *
     * <p>In most cases this should be set to the current rotation returned by {@link
     * Display#getRotation()}.
     *
     * @param rotation Desired rotation of the output video.
     */
    public void setTargetRotation(@RotationValue int rotation) {
        VideoCaptureConfig oldConfig = (VideoCaptureConfig) getUseCaseConfig();
        VideoCaptureConfig.Builder builder = VideoCaptureConfig.Builder.fromConfig(oldConfig);
        int oldRotation = oldConfig.getTargetRotation(ImageOutputConfig.INVALID_ROTATION);
        if (oldRotation == ImageOutputConfig.INVALID_ROTATION || oldRotation != rotation) {
            UseCaseConfigUtil.updateTargetRotationAndRelatedConfigs(builder, rotation);
            updateUseCaseConfig(builder.getUseCaseConfig());

            // TODO(b/122846516): Update session configuration and possibly reconfigure session.
        }
    }

    /**
     * Setup the {@link MediaCodec} for encoding video from a camera {@link Surface} and encoding
     * audio from selected audio source.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    void setupEncoder(@NonNull String cameraId, @NonNull Size resolution) {
        VideoCaptureConfig config = (VideoCaptureConfig) getUseCaseConfig();

        // video encoder setup
        mVideoEncoder.reset();
        mVideoEncoder.configure(
            createMediaFormat(config, resolution), /*surface*/
            null, /*crypto*/
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (mCameraSurface != null) {
            releaseCameraSurface(false);
        }
        Surface cameraSurface = mVideoEncoder.createInputSurface();
        mCameraSurface = cameraSurface;

        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);

        if (mDeferrableSurface != null) {
            mDeferrableSurface.close();
        }
        mDeferrableSurface = new ImmediateSurface(mCameraSurface);
        mDeferrableSurface.getTerminationFuture().addListener(
            cameraSurface::release, CameraXExecutors.mainThreadExecutor()
        );

        sessionConfigBuilder.addSurface(mDeferrableSurface);

        sessionConfigBuilder.addErrorListener(new SessionConfig.ErrorListener() {
            @Override
            public void onError(@NonNull SessionConfig sessionConfig,
                                @NonNull SessionConfig.SessionError error) {
                // Ensure the bound camera has not changed before calling setupEncoder.
                // TODO(b/143915543): Ensure this never gets called by a camera that is not bound
                //  to this use case so we don't need to do this check.
                if (isCurrentlyBoundCamera(cameraId)) {
                    // Only reset the pipeline when the bound camera is the same.
                    setupEncoder(cameraId, resolution);
                }
            }
        });

        attachToCamera(cameraId, sessionConfigBuilder.build());

        // audio encoder setup
        setAudioParametersByCamcorderProfile(resolution, cameraId);
        mAudioEncoder.reset();
        mAudioEncoder.configure(
            createAudioMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        if (mAudioRecorder != null) {
            mAudioRecorder.release();
        }
        mAudioRecorder = autoConfigAudioRecordSource(config);
        // check mAudioRecorder
        if (mAudioRecorder == null) {
            Log.e(TAG, "AudioRecord object cannot initialized correctly!");
        }

        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
        mIsRecording = false;
    }

    /**
     * Write a buffer that has been encoded to file.
     *
     * @param bufferIndex the index of the buffer in the videoEncoder that has available data
     * @return returns true if this buffer is the end of the stream
     */
    private boolean writeVideoEncodedBuffer(int bufferIndex) {
        if (bufferIndex < 0) {
            Log.e(TAG, "Output buffer should not have negative index: " + bufferIndex);
            return false;
        }
        // Get data from buffer
        ByteBuffer outputBuffer = mVideoEncoder.getOutputBuffer(bufferIndex);

        // Check if buffer is valid, if not then return
        if (outputBuffer == null) {
            Log.d(TAG, "OutputBuffer was null.");
            return false;
        }

        // Write data to mMuxer if available
        if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0 && mVideoBufferInfo.size > 0) {
            outputBuffer.position(mVideoBufferInfo.offset);
            outputBuffer.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
            mVideoBufferInfo.presentationTimeUs = (System.nanoTime() / 1000);

            synchronized (mMuxerLock) {
                if (!mIsFirstVideoSampleWrite.get()) {
                    Log.i(TAG, "First video sample written.");
                    mIsFirstVideoSampleWrite.set(true);
                }
                mMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, mVideoBufferInfo);
            }
        }

        // Release data
        mVideoEncoder.releaseOutputBuffer(bufferIndex, false);

        // Return true if EOS is set
        return (mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    private boolean writeAudioEncodedBuffer(int bufferIndex) {
        ByteBuffer buffer = getOutputBuffer(mAudioEncoder, bufferIndex);
        buffer.position(mAudioBufferInfo.offset);
        if (mAudioTrackIndex >= 0
            && mVideoTrackIndex >= 0
            && mAudioBufferInfo.size > 0
            && mAudioBufferInfo.presentationTimeUs > 0) {
            try {
                synchronized (mMuxerLock) {
                    if (!mIsFirstAudioSampleWrite.get()) {
                        Log.i(TAG, "First audio sample written.");
                        mIsFirstAudioSampleWrite.set(true);
                    }
                    mMuxer.writeSampleData(mAudioTrackIndex, buffer, mAudioBufferInfo);
                }
            } catch (Exception e) {
                Log.e(
                    TAG,
                    "audio error:size="
                        + mAudioBufferInfo.size
                        + "/offset="
                        + mAudioBufferInfo.offset
                        + "/timeUs="
                        + mAudioBufferInfo.presentationTimeUs);
                e.printStackTrace();
            }
        }
        mAudioEncoder.releaseOutputBuffer(bufferIndex, false);
        return (mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    /**
     * Encoding which runs indefinitely until end of stream is signaled. This should not run on the
     * main thread otherwise it will cause the application to block.
     *
     * @return returns {@code true} if an error condition occurred, otherwise returns {@code false}
     */
    boolean videoEncode(@NonNull OnVideoSavedCallback videoSavedCallback, @NonNull String cameraId,
                        @NonNull Size resolution) {
        // Main encoding loop. Exits on end of stream.
        boolean errorOccurred = false;
        boolean videoEos = false;
        while (!videoEos && !errorOccurred) {
            // Check for end of stream from main thread
            if (mEndOfVideoStreamSignal.get()) {
                mVideoEncoder.signalEndOfInputStream();
                mEndOfVideoStreamSignal.set(false);
            }

            // Deque buffer to check for processing step
            int outputBufferId =
                mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, DEQUE_TIMEOUT_USEC);
            switch (outputBufferId) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    if (mMuxerStarted) {
                        videoSavedCallback.onError(
                            ERROR_ENCODER,
                            "Unexpected change in video encoding format.",
                            null);
                        errorOccurred = true;
                    }

                    synchronized (mMuxerLock) {
                        mVideoTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());
                        if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                            mMuxerStarted = true;
                            Log.i(TAG, "media mMuxer start");
                            mMuxer.start();
                        }
                    }
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    // Timed out. Just wait until next attempt to deque.
                default:
                    videoEos = writeVideoEncodedBuffer(outputBufferId);
            }
        }

        try {
            Log.i(TAG, "videoEncoder stop");
            mVideoEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                "Video encoder stop failed!", e);
            errorOccurred = true;
        }

        try {
            // new MediaMuxer instance required for each new file written, and release current one.
            synchronized (mMuxerLock) {
                if (mMuxer != null) {
                    if (mMuxerStarted) {
                        mMuxer.stop();
                    }
                    mMuxer.release();
                    mMuxer = null;
                }
            }
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_MUXER, "Muxer stop failed!", e);
            errorOccurred = true;
        }

        mMuxerStarted = false;
        // Do the setup of the videoEncoder at the end of video recording instead of at the start of
        // recording because it requires attaching a new Surface. This causes a glitch so we don't
        // want that to incur latency at the start of capture.
        setupEncoder(cameraId, resolution);
        notifyReset();

        // notify the UI thread that the video recording has finished
        mEndOfAudioVideoSignal.set(true);

        Log.i(TAG, "Video encode thread end.");
        return errorOccurred;
    }

    boolean audioEncode(OnVideoSavedCallback videoSavedCallback) {
        // Audio encoding loop. Exits on end of stream.
        boolean audioEos = false;
        int outIndex;
        while (!audioEos && mIsRecording) {
            // Check for end of stream from main thread
            if (mEndOfAudioStreamSignal.get()) {
                mEndOfAudioStreamSignal.set(false);
                mIsRecording = false;
            }

            // get audio deque input buffer
            if (mAudioEncoder != null && mAudioRecorder != null) {
                int index = mAudioEncoder.dequeueInputBuffer(-1);
                if (index >= 0) {
                    final ByteBuffer buffer = getInputBuffer(mAudioEncoder, index);
                    buffer.clear();
                    int length = mAudioRecorder.read(buffer, mAudioBufferSize);
                    if (length > 0) {
                        mAudioEncoder.queueInputBuffer(
                            index,
                            0,
                            length,
                            (System.nanoTime() / 1000),
                            mIsRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                // start to dequeue audio output buffer
                do {
                    outIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, 0);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            synchronized (mMuxerLock) {
                                mAudioTrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());
                                if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                                    mMuxerStarted = true;
                                    mMuxer.start();
                                }
                            }
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            break;
                        default:
                            audioEos = writeAudioEncodedBuffer(outIndex);
                    }
                } while (outIndex >= 0 && !audioEos); // end of dequeue output buffer
            }
        } // end of while loop

        // Audio Stop
        try {
            Log.i(TAG, "audioRecorder stop");
            // Begin Signal Custom Code Block
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
            } else {
                Log.w(TAG, "Missing audio recorder in stop()!");
            }
            // End Signal Custom Code Block
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(
                ERROR_ENCODER, "Audio recorder stop failed!", e);
        }

        try {
            mAudioEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                "Audio encoder stop failed!", e);
        }

        Log.i(TAG, "Audio encode thread end");
        // Use AtomicBoolean to signal because MediaCodec.signalEndOfInputStream() is not thread
        // safe
        mEndOfVideoStreamSignal.set(true);

        return false;
    }

    private ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        return codec.getInputBuffer(index);
    }

    private ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        return codec.getOutputBuffer(index);
    }

    /** Creates a {@link MediaFormat} using parameters for audio from the configuration */
    private MediaFormat createAudioMediaFormat() {
        MediaFormat format =
            MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mAudioSampleRate,
                mAudioChannelCount);
        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitRate);

        return format;
    }

    /** Create a AudioRecord object to get raw data */
    private AudioRecord autoConfigAudioRecordSource(VideoCaptureConfig config) {
        for (short audioFormat : sAudioEncoding) {

            // Use channel count to determine stereo vs mono
            int channelConfig =
                mAudioChannelCount == 1
                    ? AudioFormat.CHANNEL_IN_MONO
                    : AudioFormat.CHANNEL_IN_STEREO;
            int source = config.getAudioRecordSource();

            try {
                int bufferSize =
                    AudioRecord.getMinBufferSize(mAudioSampleRate, channelConfig, audioFormat);

                if (bufferSize <= 0) {
                    bufferSize = config.getAudioMinBufferSize();
                }

                AudioRecord recorder =
                    new AudioRecord(
                        source,
                        mAudioSampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize * 2);

                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    mAudioBufferSize = bufferSize;
                    Log.i(
                        TAG,
                        "source: "
                            + source
                            + " audioSampleRate: "
                            + mAudioSampleRate
                            + " channelConfig: "
                            + channelConfig
                            + " audioFormat: "
                            + audioFormat
                            + " bufferSize: "
                            + bufferSize);
                    return recorder;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception, keep trying.", e);
            }
        }

        return null;
    }

    /** Set audio record parameters by CamcorderProfile */
    private void setAudioParametersByCamcorderProfile(Size currentResolution, String cameraId) {
        CamcorderProfile profile;
        boolean isCamcorderProfileFound = false;

        for (int quality : CamcorderQuality) {
            if (CamcorderProfile.hasProfile(Integer.parseInt(cameraId), quality)) {
                profile = CamcorderProfile.get(Integer.parseInt(cameraId), quality);
                if (currentResolution.getWidth() == profile.videoFrameWidth
                    && currentResolution.getHeight() == profile.videoFrameHeight) {
                    mAudioChannelCount = profile.audioChannels;
                    mAudioSampleRate = profile.audioSampleRate;
                    mAudioBitRate = profile.audioBitRate;
                    isCamcorderProfileFound = true;
                    break;
                }
            }
        }

        // In case no corresponding camcorder profile can be founded, * get default value from
        // VideoCaptureConfig.
        if (!isCamcorderProfileFound) {
            VideoCaptureConfig config = (VideoCaptureConfig) getUseCaseConfig();
            mAudioChannelCount = config.getAudioChannelCount();
            mAudioSampleRate = config.getAudioSampleRate();
            mAudioBitRate = config.getAudioBitRate();
        }
    }

    /**
     * Describes the error that occurred during video capture operations.
     *
     * <p>This is a parameter sent to the error callback functions set in listeners such as {@link
     * VideoCapture.OnVideoSavedCallback#onError(int, String, Throwable)}.
     *
     * <p>See message parameter in onError callback or log for more details.
     *
     * @hide
     */
    @IntDef({ERROR_UNKNOWN, ERROR_ENCODER, ERROR_MUXER, ERROR_RECORDING_IN_PROGRESS})
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(Scope.LIBRARY_GROUP)
    public @interface VideoCaptureError {
    }

    /** Listener containing callbacks for video file I/O events. */
    public interface OnVideoSavedCallback {
        /** Called when the video has been successfully saved. */
        // TODO: Should remove file argument to match ImageCapture.OnImageSavedCallback
        //  #onImageSaved()
        // Begin Signal Custom Code Block
        void onVideoSaved(@NonNull FileDescriptor file);
        // End Signal Custom Code Block

        /** Called when an error occurs while attempting to save the video. */
        void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                     @Nullable Throwable cause);
    }

    /**
     * Provides a base static default configuration for the VideoCapture
     *
     * <p>These values may be overridden by the implementation. They only provide a minimum set of
     * defaults that are implementation independent.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Defaults
        implements ConfigProvider<VideoCaptureConfig> {
        private static final int DEFAULT_VIDEO_FRAME_RATE = 30;
        /** 8Mb/s the recommend rate for 30fps 1080p */
        private static final int DEFAULT_BIT_RATE = 8 * 1024 * 1024;
        /** Seconds between each key frame */
        private static final int DEFAULT_INTRA_FRAME_INTERVAL = 1;
        /** audio bit rate */
        private static final int DEFAULT_AUDIO_BIT_RATE = 64000;
        /** audio sample rate */
        private static final int DEFAULT_AUDIO_SAMPLE_RATE = 8000;
        /** audio channel count */
        private static final int DEFAULT_AUDIO_CHANNEL_COUNT = 1;
        /** audio record source */
        private static final int DEFAULT_AUDIO_RECORD_SOURCE = AudioSource.MIC;
        /** audio default minimum buffer size */
        private static final int DEFAULT_AUDIO_MIN_BUFFER_SIZE = 1024;
        /** Current max resolution of VideoCapture is set as FHD */
        private static final Size DEFAULT_MAX_RESOLUTION = new Size(1920, 1080);
        /** Surface occupancy prioirty to this use case */
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 3;

        private static final VideoCaptureConfig DEFAULT_CONFIG;

        static {
            VideoCaptureConfig.Builder builder =
                new VideoCaptureConfig.Builder()
                    .setVideoFrameRate(DEFAULT_VIDEO_FRAME_RATE)
                    .setBitRate(DEFAULT_BIT_RATE)
                    .setIFrameInterval(DEFAULT_INTRA_FRAME_INTERVAL)
                    .setAudioBitRate(DEFAULT_AUDIO_BIT_RATE)
                    .setAudioSampleRate(DEFAULT_AUDIO_SAMPLE_RATE)
                    .setAudioChannelCount(DEFAULT_AUDIO_CHANNEL_COUNT)
                    .setAudioRecordSource(DEFAULT_AUDIO_RECORD_SOURCE)
                    .setAudioMinBufferSize(DEFAULT_AUDIO_MIN_BUFFER_SIZE)
                    .setMaxResolution(DEFAULT_MAX_RESOLUTION)
                    .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);

            DEFAULT_CONFIG = builder.getUseCaseConfig();
        }

        @NonNull
        @Override
        public VideoCaptureConfig getConfig(@Nullable CameraInfo cameraInfo) {
            return DEFAULT_CONFIG;
        }
    }

    /** Holder class for metadata that should be saved alongside captured video. */
    public static final class Metadata {
        /** Data representing a geographic location. */
        @Nullable
        public Location location;
    }

    private final class VideoSavedListenerWrapper implements OnVideoSavedCallback {

        @NonNull
        Executor mExecutor;
        @NonNull
        OnVideoSavedCallback mOnVideoSavedCallback;

        VideoSavedListenerWrapper(@NonNull Executor executor,
                                  @NonNull OnVideoSavedCallback onVideoSavedCallback) {
            mExecutor = executor;
            mOnVideoSavedCallback = onVideoSavedCallback;
        }

        @Override
        // Begin Signal Custom Code Block
        public void onVideoSaved(@NonNull FileDescriptor file) {
        // End Signal Custom Code Block
            try {
                mExecutor.execute(() -> mOnVideoSavedCallback.onVideoSaved(file));
            } catch (RejectedExecutionException e) {
                Log.e(TAG, "Unable to post to the supplied executor.");
            }
        }

        @Override
        public void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                            @Nullable Throwable cause) {
            try {
                mExecutor.execute(
                    () -> mOnVideoSavedCallback.onError(videoCaptureError, message, cause));
            } catch (RejectedExecutionException e) {
                Log.e(TAG, "Unable to post to the supplied executor.");
            }
        }

    }
}
