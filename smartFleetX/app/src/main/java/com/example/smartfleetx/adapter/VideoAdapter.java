package com.example.smartfleetx.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfleetx.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private final Context context;
    private final List<VideoFile> videoFiles;
    private final OnVideoClickListener listener;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /** Wraps a File with its source label and cached metadata */
    public static class VideoFile {
        public final File file;
        public final String source; // "ESP32 Stream" or "DashCam"

        // Cached metadata — loaded once, reused on every scroll
        public volatile Bitmap cachedThumbnail;
        public volatile String cachedDuration;
        public volatile boolean metadataLoaded = false;

        public VideoFile(File file, String source) {
            this.file = file;
            this.source = source;
        }
    }

    public interface OnVideoClickListener {
        void onVideoClick(File file);
        void onVideoDelete(File file);
    }

    public VideoAdapter(Context context, List<VideoFile> videoFiles, OnVideoClickListener listener) {
        this.context = context;
        this.videoFiles = videoFiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoFile vf = videoFiles.get(position);
        holder.bind(vf);
    }

    @Override
    public int getItemCount() {
        return videoFiles.size();
    }

    public class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvFilename, tvDetails, tvSourceBadge, tvDuration;
        ImageButton btnPlay, btnDelete;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail   = itemView.findViewById(R.id.ivThumbnail);
            tvFilename    = itemView.findViewById(R.id.tvFilename);
            tvDetails     = itemView.findViewById(R.id.tvDetails);
            tvSourceBadge = itemView.findViewById(R.id.tvSourceBadge);
            tvDuration    = itemView.findViewById(R.id.tvDuration);
            btnPlay       = itemView.findViewById(R.id.btnPlay);
            btnDelete     = itemView.findViewById(R.id.btnDelete);

            // Disable Text Classification for video items to avoid main-thread overhead
            disableTextClassification(tvFilename, tvDetails, tvSourceBadge, tvDuration);
        }

        private void disableTextClassification(android.widget.TextView... views) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                for (android.widget.TextView view : views) {
                    if (view != null) {
                        view.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP);
                    }
                }
            }
        }

        public void bind(VideoFile vf) {
            File file = vf.file;

            // Tag this view with the current file path so async callbacks can detect
            // if the ViewHolder has been recycled/rebound before they complete.
            ivThumbnail.setTag(file.getAbsolutePath());

            // Filename
            tvFilename.setText(file.getName());

            // Size + date
            String size = Formatter.formatShortFileSize(context, file.length());
            String date = new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
                    .format(new Date(file.lastModified()));
            tvDetails.setText(String.format("%s • %s", size, date));

            // Source badge
            boolean isEsp32 = "ESP32 Stream".equals(vf.source);
            tvSourceBadge.setText(isEsp32 ? "📡 ESP32 Stream" : "📹 DashCam");
            tvSourceBadge.setBackgroundColor(isEsp32
                    ? 0xDD1565C0    // blue for ESP32
                    : 0xDD2E7D32);  // green for DashCam

            // If metadata is already cached, apply immediately — no thread needed
            if (vf.metadataLoaded) {
                if (vf.cachedThumbnail != null) {
                    ivThumbnail.setImageBitmap(vf.cachedThumbnail);
                } else {
                    ivThumbnail.setImageResource(android.R.drawable.ic_menu_camera);
                }
                tvDuration.setText(vf.cachedDuration != null ? vf.cachedDuration : "--:--");
                return;
            }

            // Reset while loading
            ivThumbnail.setImageResource(android.R.drawable.ic_menu_camera);
            tvDuration.setText("--:--");

            // Load thumbnail + duration asynchronously and cache the result
            final String filePath = file.getAbsolutePath();
            executor.execute(() -> {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                Bitmap thumb = null;
                String durationStr = "--:--";
                try {
                    retriever.setDataSource(filePath);

                    // Get first frame as thumbnail
                    thumb = retriever.getFrameAtTime(1_000_000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                    // Get duration
                    String durationMs = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationMs != null) {
                        long millis = Long.parseLong(durationMs);
                        long mins   = (millis / 1000) / 60;
                        long secs   = (millis / 1000) % 60;
                        durationStr = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
                    }
                } catch (Exception ignored) {
                    // If metadata fails, generic icon stays
                } finally {
                    try { retriever.release(); } catch (Exception ignored) {}
                }

                // Store in cache on the VideoFile object so future binds are free
                vf.cachedThumbnail = thumb;
                vf.cachedDuration  = durationStr;
                vf.metadataLoaded  = true;

                final Bitmap finalThumb    = thumb;
                final String finalDuration = durationStr;

                // Guard: only update UI if this ViewHolder still shows the same file
                ivThumbnail.post(() -> {
                    if (filePath.equals(ivThumbnail.getTag())) {
                        if (finalThumb != null) ivThumbnail.setImageBitmap(finalThumb);
                        tvDuration.setText(finalDuration);
                    }
                });
            });

            // Click listeners
            itemView.setOnClickListener(v -> { if (listener != null) listener.onVideoClick(file); });
            btnPlay.setOnClickListener(v -> { if (listener != null) listener.onVideoClick(file); });
            btnDelete.setOnClickListener(v -> { if (listener != null) listener.onVideoDelete(file); });
        }
    }
}
