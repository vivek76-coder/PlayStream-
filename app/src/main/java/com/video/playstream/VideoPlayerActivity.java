package com.video.playstream;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";
    private PlayerView playerView;
    private ExoPlayer player;
    private Uri videoUri;
    private long playbackPosition = 0;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while playing video
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_player);

        // Hide status bar and navigation bar for full screen experience
        // Must be AFTER setContentView so DecorView exists
        hideSystemUI();

        playerView = findViewById(R.id.playerView);

        // Handle Intent (both from app and external apps)
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
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void initializePlayer() {
        if (player == null && videoUri != null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            // Add error listener
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
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
        if (Build.VERSION.SDK_INT < 24 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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
}
