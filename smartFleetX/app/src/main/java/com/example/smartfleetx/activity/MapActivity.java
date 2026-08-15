package com.example.smartfleetx.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartfleetx.R;
import com.example.smartfleetx.BuildConfig;
import com.example.smartfleetx.network.ApiService;
import com.example.smartfleetx.network.RetrofitClient;
import com.google.gson.JsonObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MapActivity - Live location tracking with OSMDroid
 * Features:
 * - Real-time API location updates
 * - Current location marker
 * - Path tracking with polyline
 * - Speed, coordinates, and Force display
 * - Center button to focus on current location
 */
public class MapActivity extends AppCompatActivity {

    // UI Components
    private MapView map;
    private TextView tvSpeed, tvLatitude, tvLongitude, tvAccuracy, tvForce, tvPWM;
    private FloatingActionButton btnCenterLocation;
    private Button btnBack;

    // Map overlays
    private Marker currentLocationMarker;
    private Polyline pathPolyline;

    // Location tracking
    private GeoPoint currentGeoPoint;
    private List<GeoPoint> pathPoints = new ArrayList<>();
    
    // API Polling
    private ApiService apiService;
    private Handler pollHandler;
    private Runnable pollRunnable;
    private boolean isTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load configuration for Osmdroid
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        setContentView(R.layout.activity_map);
        
        apiService = RetrofitClient.getApiService();

        initializeViews();
        setupMap();
        setupListeners();

        initializeLiveLocation();
    }

    private void initializeViews() {
        map = findViewById(R.id.map);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        tvForce = findViewById(R.id.tvForce);
        tvPWM = findViewById(R.id.tvPWM);
        btnCenterLocation = findViewById(R.id.btnCenterLocation);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(true);

        // Set default zoom and center (Pune, India)
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(18.5204, 73.8567));

        // Initialize path polyline
        pathPolyline = new Polyline();
        pathPolyline.setColor(Color.BLUE);
        pathPolyline.setWidth(5f);
        map.getOverlays().add(pathPolyline);

        // Add fleet vehicle markers (optional)
        addFleetMarker(18.5204, 73.8567, "Vehicle 1", "KA-01-AB-1234");
        addFleetMarker(18.5304, 73.8667, "Vehicle 2", "MH-12-CD-9876");
    }

    private void setupListeners() {
        if (btnCenterLocation != null) {
            btnCenterLocation.setOnClickListener(v -> centerOnCurrentLocation());
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void addFleetMarker(double lat, double lon, String title, String vehicle) {
        Marker marker = new Marker(map);
        marker.setPosition(new GeoPoint(lat, lon));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        marker.setSnippet("Vehicle: " + vehicle);
        map.getOverlays().add(marker);
    }

    /**
     * Initialize live location tracking via API
     */
    private void initializeLiveLocation() {
        // Create custom marker for current location
        currentLocationMarker = new Marker(map);
        currentLocationMarker.setTitle("Fleet Vehicle");
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(currentLocationMarker);

        tvAccuracy.setText("API Sync"); // No GPS accuracy in API right now
        
        isTracking = true;
        Toast.makeText(this, "📡 Syncing location from Hardware API", Toast.LENGTH_SHORT).show();

        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    fetchLatestLocation();
                    pollHandler.postDelayed(this, 2000); // Poll every 2 seconds
                }
            }
        };
        pollHandler.post(pollRunnable);
    }

    private void fetchLatestLocation() {
        apiService.getLatestHardwareData().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject resBody = response.body();
                    if (resBody.get("success").getAsBoolean() && resBody.has("data") && !resBody.get("data").isJsonNull()) {
                        JsonObject data = resBody.getAsJsonObject("data");
                        
                        double lat = data.get("lat").getAsDouble();
                        double lng = data.get("lang").getAsDouble();
                        double speed = data.get("speed").getAsDouble();
                        double force = data.has("force") ? data.get("force").getAsDouble() : 0.0;
                        int pwm = data.has("pwm") ? data.get("pwm").getAsInt() : 0;
                        
                        updateMapLocation(lat, lng, speed, force, pwm);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Silently fail, try again in 2 seconds
            }
        });
    }

    private void updateMapLocation(double lat, double lng, double speed, double force, int pwm) {
        currentGeoPoint = new GeoPoint(lat, lng);

        // Update marker position
        if (currentLocationMarker != null) {
            currentLocationMarker.setPosition(currentGeoPoint);
            currentLocationMarker.setSnippet(String.format(Locale.getDefault(),
                    "Speed: %.1f km/h\\nForce: %.2f G\\nPWM: %d",
                    speed, force, pwm));
        }

        // Add point to path (only if it moved significantly to avoid clutter, using 0.0001 roughly 10m)
        if (pathPoints.isEmpty() || 
            Math.abs(pathPoints.get(pathPoints.size()-1).getLatitude() - lat) > 0.00005 ||
            Math.abs(pathPoints.get(pathPoints.size()-1).getLongitude() - lng) > 0.00005) {
            
            pathPoints.add(currentGeoPoint);
            pathPolyline.setPoints(pathPoints);
        }

        // Update UI text views
        if (tvLatitude != null) tvLatitude.setText(String.format(Locale.getDefault(), "Lat: %.6f°", lat));
        if (tvLongitude != null) tvLongitude.setText(String.format(Locale.getDefault(), "Lng: %.6f°", lng));
        if (tvSpeed != null) tvSpeed.setText(String.format(Locale.getDefault(), "%.1f km/h", speed));
        if (tvForce != null) tvForce.setText(String.format(Locale.getDefault(), "%.2f G", force));
        if (tvPWM != null) tvPWM.setText(String.valueOf(pwm));

        // Refresh map
        map.invalidate();
        
        // Auto center on first load
        if (pathPoints.size() == 1) {
            centerOnCurrentLocation();
        }
    }

    /**
     * Center map on current location
     */
    private void centerOnCurrentLocation() {
        if (currentGeoPoint != null) {
            map.getController().animateTo(currentGeoPoint);
            map.getController().setZoom(17.0);
            Toast.makeText(this, "Centered on vehicle location", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        isTracking = true;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.post(pollRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
        isTracking = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop API Polling
        isTracking = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }

        // Clean up map
        if (map != null) {
            map.onDetach();
        }
    }
}
