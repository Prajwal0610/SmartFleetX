package com.example.smartfleetx.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfleetx.R;
import com.example.smartfleetx.adapter.VideoAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VideoPlaybackActivity – Recording Library
 *
 * Shows all recorded MP4 videos from:
 *   • Recordings/  (ESP32 IP stream recordings)
 *   • DashCam/     (dashcam recordings)
 *
 * Newest videos appear first. Each card shows:
 *   source badge, thumbnail, duration, size+date, play & delete buttons.
 */
public class VideoPlaybackActivity extends AppCompatActivity
        implements VideoAdapter.OnVideoClickListener {

    private RecyclerView rvVideos;
    private View tvEmptyState;
    private TextView tvRecordingCount;
    private VideoAdapter adapter;
    private List<VideoAdapter.VideoFile> videoFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_playback);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("📹 Recording Library");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvVideos          = findViewById(R.id.rvVideos);
        tvEmptyState      = findViewById(R.id.tvEmptyState);
        tvRecordingCount  = findViewById(R.id.tvRecordingCount);

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        videoFiles = new ArrayList<>();
        adapter    = new VideoAdapter(this, videoFiles, this);
        rvVideos.setAdapter(adapter);

        loadVideos();
    }

    private void loadVideos() {
        new Thread(() -> {
            final List<VideoAdapter.VideoFile> foundFiles = new ArrayList<>();

            // ── 1. ESP32 Stream recordings ──────────────────────────────
            File recDir = new File(getExternalFilesDir(null), "Recordings");
            if (recDir.exists() && recDir.isDirectory()) {
                File[] esp32Files = recDir.listFiles(
                        (dir, name) -> name.toLowerCase().endsWith(".mp4"));
                if (esp32Files != null) {
                    for (File f : esp32Files) {
                        foundFiles.add(new VideoAdapter.VideoFile(f, "ESP32 Stream"));
                    }
                }
            }

            // ── 2. DashCam recordings ───────────────────────────────────
            File dashDir = new File(getExternalFilesDir(null), "DashCam");
            if (dashDir.exists() && dashDir.isDirectory()) {
                File[] dashFiles = dashDir.listFiles(
                        (dir, name) -> name.toLowerCase().endsWith(".mp4"));
                if (dashFiles != null) {
                    for (File f : dashFiles) {
                        foundFiles.add(new VideoAdapter.VideoFile(f, "DashCam"));
                    }
                }
            }

            // ── Sort newest first ───────────────────────────────────────
            Collections.sort(foundFiles,
                    (a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));

            // Update UI on main thread
            runOnUiThread(() -> {
                videoFiles.clear();
                videoFiles.addAll(foundFiles);
                adapter.notifyDataSetChanged();

                if (videoFiles.isEmpty()) {
                    showEmptyState();
                } else {
                    tvEmptyState.setVisibility(View.GONE);
                    rvVideos.setVisibility(View.VISIBLE);
                    if (tvRecordingCount != null) {
                        tvRecordingCount.setVisibility(View.VISIBLE);
                        tvRecordingCount.setText(videoFiles.size() + " recording(s) found");
                    }
                }
            });
        }).start();
    }

    private void showEmptyState() {
        tvEmptyState.setVisibility(View.VISIBLE);
        rvVideos.setVisibility(View.GONE);
        if (tvRecordingCount != null) {
            tvRecordingCount.setVisibility(View.GONE);
        }
    }

    @Override
    public void onVideoClick(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this,
                    "No video player found: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onVideoDelete(File file) {
        if (file.exists() && file.delete()) {
            // Optimistically remove from list and update adapter to avoid full rescan
            for (int i = 0; i < videoFiles.size(); i++) {
                if (videoFiles.get(i).file.equals(file)) {
                    videoFiles.remove(i);
                    adapter.notifyItemRemoved(i);
                    break;
                }
            }
            
            if (videoFiles.isEmpty()) showEmptyState();
            else if (tvRecordingCount != null) {
                tvRecordingCount.setText(videoFiles.size() + " recording(s) found");
            }
            
            Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not delete recording", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVideos(); // refresh whenever we return (e.g. after new recording)
    }
}
