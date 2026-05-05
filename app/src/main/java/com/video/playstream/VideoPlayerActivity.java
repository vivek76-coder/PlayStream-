package com.video.playstream;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";

    // Player
    private PlayerView playerView;
    private ExoPlayer player;
    private Uri videoUri;
    private long playbackPosition = 0;
    private boolean playWhenReady = true;

    // Gesture detectors
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    // Audio
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;

    // Zoom
    private float scaleFactor = 1.0f;

    // Resize modes
    private int currentResizeMode = 0;
    private final int[] resizeModes = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    };
    private final String[] resizeModeNames = {"Fit", "Fill", "Zoom"};

    // UI Indicators
    private LinearLayout volumeIndicator;
    private LinearLayout brightnessIndicator;
    private LinearLayout zoomIndicator;
    private ProgressBar volumeProgress;
    private ProgressBar brightnessProgress;
    private TextView volumeText;
    private TextView brightnessText;
    private TextView zoomText;
    private TextView resizeModeText;
    private ImageView volumeIcon;

    // Gesture state
    private boolean isSwipingVolume = false;
    private boolean isSwipingBrightness = false;
    private float initialY = 0;
    private float currentBrightness = -1f;

    // Hide indicator handler
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideVolumeRunnable = () -> volumeIndicator.setVisibility(View.GONE);
    private final Runnable hideBrightnessRunnable = () -> brightnessIndicator.setVisibility(View.GONE);
    private final Runnable hideZoomRunnable = () -> zoomIndicator.setVisibility(View.GONE);
    private final Runnable hideResizeModeRunnable = () -> resizeModeText.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_player);

        // Must be AFTER setContentView
        hideSystemUI();

        // Initialize views
        playerView = findViewById(R.id.playerView);
        volumeIndicator = findViewById(R.id.volumeIndicator);
        brightnessIndicator = findViewById(R.id.brightnessIndicator);
        zoomIndicator = findViewById(R.id.zoomIndicator);
        volumeProgress = findViewById(R.id.volumeProgress);
        brightnessProgress = findViewById(R.id.brightnessProgress);
        volumeText = findViewById(R.id.volumeText);
        brightnessText = findViewById(R.id.brightnessText);
        zoomText = findViewById(R.id.zoomText);
        resizeModeText = findViewById(R.id.resizeModeText);
        volumeIcon = findViewById(R.id.volumeIcon);

        // Audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // Get current brightness
        currentBrightness = getWindow().getAttributes().screenBrightness;
        if (currentBrightness < 0) {
            try {
                currentBrightness = android.provider.Settings.System.getInt(
                        getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
            } catch (Exception e) {
                currentBrightness = 0.5f;
            }
        }

        // Setup gesture detectors
        setupGestures();

        // Handle Intent
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            videoUri = intent.getData();
        } else {
            String uriString = intent.getStringExtra("VIDEO_URI");
            if (uriString != null) {
                videoUri = Uri.parse(uriString);
            }
        }

        Log.d(TAG, "Video URI: " + videoUri);

        if (videoUri == null) {
            Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void setupGestures() {
        // Pinch-to-zoom
        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        scaleFactor *= detector.getScaleFactor();
                        scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

                        // Apply zoom to the video surface
                        View videoSurface = playerView.getVideoSurfaceView();
                        if (videoSurface != null) {
                            videoSurface.setScaleX(scaleFactor);
                            videoSurface.setScaleY(scaleFactor);
                        }

                        // Show zoom indicator
                        showZoomIndicator();
                        return true;
                    }
                });

        // Swipe gestures for volume & brightness + double-tap
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        // Double tap left = rewind 10s, right = forward 10s
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        if (player != null) {
                            if (e.getX() < screenWidth / 3f) {
                                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                                Toast.makeText(VideoPlayerActivity.this, "⏪ -10s", Toast.LENGTH_SHORT).show();
                            } else if (e.getX() > screenWidth * 2f / 3f) {
                                player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                                Toast.makeText(VideoPlayerActivity.this, "⏩ +10s", Toast.LENGTH_SHORT).show();
                            } else {
                                // Double tap center = toggle resize mode
                                toggleResizeMode();
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        // Toggle controls visibility
                        if (playerView.isControllerFullyVisible()) {
                            playerView.hideController();
                        } else {
                            playerView.showController();
                        }
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2,
                                            float distanceX, float distanceY) {
                        if (e1 == null) return false;

                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;

                        // Only handle vertical swipes
                        if (Math.abs(distanceY) < Math.abs(distanceX)) return false;

                        float x = e1.getX();
                        float deltaY = e1.getY() - e2.getY();
                        float swipePercent = deltaY / (screenHeight * 0.6f);

                        if (x < screenWidth / 2f) {
                            // Left side = Brightness
                            adjustBrightness(swipePercent);
                        } else {
                            // Right side = Volume
                            adjustVolume(swipePercent);
                        }
                        return true;
                    }
                });

        // Apply gesture listeners to PlayerView
        playerView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                isSwipingVolume = false;
                isSwipingBrightness = false;
            }

            return true;
        });
    }

    private void adjustVolume(float swipePercent) {
        int newVolume = (int) (currentVolume + (swipePercent * maxVolume));
        newVolume = Math.max(0, Math.min(newVolume, maxVolume));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);

        int percent = (int) ((newVolume / (float) maxVolume) * 100);

        // Update UI
        volumeProgress.setProgress(percent);
        volumeText.setText(percent + "%");
        volumeIcon.setImageResource(newVolume == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);

        showIndicator(volumeIndicator, hideVolumeRunnable);
    }

    private void adjustBrightness(float swipePercent) {
        float newBrightness = currentBrightness + swipePercent;
        newBrightness = Math.max(0.01f, Math.min(newBrightness, 1.0f));

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = newBrightness;
        getWindow().setAttributes(lp);

        int percent = (int) (newBrightness * 100);

        // Update UI
        brightnessProgress.setProgress(percent);
        brightnessText.setText(percent + "%");

        showIndicator(brightnessIndicator, hideBrightnessRunnable);
    }

    private void showZoomIndicator() {
        zoomText.setText(String.format("%.1fx", scaleFactor));
        showIndicator(zoomIndicator, hideZoomRunnable);
    }

    private void toggleResizeMode() {
        currentResizeMode = (currentResizeMode + 1) % resizeModes.length;
        playerView.setResizeMode(resizeModes[currentResizeMode]);

        // Reset zoom when changing resize mode
        scaleFactor = 1.0f;
        View videoSurface = playerView.getVideoSurfaceView();
        if (videoSurface != null) {
            videoSurface.setScaleX(1.0f);
            videoSurface.setScaleY(1.0f);
        }

        resizeModeText.setText(resizeModeNames[currentResizeMode]);
        resizeModeText.setVisibility(View.VISIBLE);
        hideHandler.removeCallbacks(hideResizeModeRunnable);
        hideHandler.postDelayed(hideResizeModeRunnable, 1500);
    }

    private void showIndicator(View indicator, Runnable hideRunnable) {
        indicator.setVisibility(View.VISIBLE);
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 1500);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void initializePlayer() {
        if (player == null && videoUri != null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            // Add error listener
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "Playback error: " + error.getMessage(), error);
                    Toast.makeText(VideoPlayerActivity.this,
                            "Error playing video: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "Playback state changed: " + playbackState);
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            player.setMediaItem(mediaItem);
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(playbackPosition);
            player.prepare();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 24) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (Build.VERSION.SDK_INT < 24 || player == null) {
            initializePlayer();
        }
        // Refresh volume
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save brightness
        currentBrightness = getWindow().getAttributes().screenBrightness;
        if (Build.VERSION.SDK_INT < 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideHandler.removeCallbacksAndMessages(null);
    }
}
