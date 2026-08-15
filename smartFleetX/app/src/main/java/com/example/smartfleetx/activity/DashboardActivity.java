package com.example.smartfleetx.activity;

import androidx.core.view.GravityCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smartfleetx.R;
import com.example.smartfleetx.adapter.IncidentAdapter;
import com.example.smartfleetx.model.Incident;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvVehiclesCount, tvAlertsCount, tvAccidentsCount, tvGreeting;
    private RecyclerView rvIncidents;
//    private FloatingActionButton fabReport;
    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private com.google.android.material.navigation.NavigationView navigationView;
    private BottomNavigationView bottomNav;

    private IncidentAdapter adapter;
    private List<Incident> mockIncidents;
    
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "app_prefs";

    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient;
    private com.google.android.gms.location.LocationCallback locationCallback;
    private android.location.Location currentLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 201;

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        checkLocationPermissions();

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup Drawer Toggle
        androidx.appcompat.app.ActionBarDrawerToggle toggle = new androidx.appcompat.app.ActionBarDrawerToggle(
                this, drawerLayout, topAppBar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_logout) {
                logout();
            } else if (id == R.id.nav_profile) { 
                 startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
            } else if (id == R.id.nav_analytics_dashboard) {
                 startActivity(new Intent(DashboardActivity.this, AnalyticsDashboardActivity.class));
            } else if (id == R.id.nav_system_health) {
                 startActivity(new Intent(DashboardActivity.this, SystemHealthActivity.class));
            } else if (id == R.id.nav_relay_controls) {
                 startActivity(new Intent(DashboardActivity.this, HardwareControlActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        tvGreeting = findViewById(R.id.tvGreeting);
        tvVehiclesCount = findViewById(R.id.tvVehiclesCount);
        tvAlertsCount = findViewById(R.id.tvAlertsCount);
        tvAccidentsCount = findViewById(R.id.tvAccidentsCount);
        rvIncidents = findViewById(R.id.rvIncidents);
//        fabReport = findViewById(R.id.fabReport);
        bottomNav = findViewById(R.id.bottomNav);

        // Get user info
        String userEmail = prefs.getString("user_email", "User");
        String userName = userEmail.split("@")[0];
        userName = userName.substring(0, 1).toUpperCase() + userName.substring(1);
        tvGreeting.setText("Welcome, " + userName);

        // Simulated stats - 18 online out of 24 total
        // Updated for Obstacle Detection
        tvVehiclesCount.setText("LIVE");
        tvAlertsCount.setText(String.valueOf(3));
        tvAccidentsCount.setText(String.valueOf(1));

        // setup recycler
        mockIncidents = createMockIncidents();
        adapter = new IncidentAdapter(mockIncidents, incident -> {
            // When "Open" is clicked, go to analysis
            Intent intent = new Intent(DashboardActivity.this, IncidentAnalysisActivity.class);
            // Optionally pass incident details
            intent.putExtra("incident_title", incident.title);
            startActivity(intent);
        });
        rvIncidents.setLayoutManager(new LinearLayoutManager(this));
        rvIncidents.setAdapter(adapter);

//        fabReport.setOnClickListener(v -> {
//            // Launch Obstacle Detection Activity (merged with Dashcam features)
//            startActivity(new Intent(DashboardActivity.this, ObstacleDetectionActivity.class));
//        });

        // Fixed: Click entire vehicle card instead of just text
        findViewById(R.id.cardVehicles).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, ObstacleDetectionActivity.class));
        });

        // Fixed: Click entire alerts card
        findViewById(R.id.cardAlerts).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, DriverHealthMonitorActivity.class));
        });

        // Fixed: Click entire accidents card
        findViewById(R.id.cardAccidents).setOnClickListener(v -> {
            if (mockIncidents != null && !mockIncidents.isEmpty()) {
                startActivity(new Intent(DashboardActivity.this, IncidentAnalysisActivity.class));
            } else {
                Toast.makeText(this, "No incidents to analyze", Toast.LENGTH_SHORT).show();
            }
        });

        // Quick Access Grid Listeners
        findViewById(R.id.cardSystemHealth).setOnClickListener(v -> 
            startActivity(new Intent(DashboardActivity.this, SystemHealthActivity.class)));

        findViewById(R.id.cardAnalytics).setOnClickListener(v -> 
            startActivity(new Intent(DashboardActivity.this, AnalyticsDashboardActivity.class)));

        findViewById(R.id.cardLiveMap).setOnClickListener(v -> 
            startActivity(new Intent(DashboardActivity.this, MapActivity.class)));

        findViewById(R.id.cardVideoLibrary).setOnClickListener(v -> 
            startActivity(new Intent(DashboardActivity.this, VideoPlaybackActivity.class)));

        // Bottom navigation
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                return true; // Already here
            } else if (itemId == R.id.nav_map) {
                startActivity(new Intent(DashboardActivity.this, MapActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        prefs.edit().putBoolean("is_logged_in", false).apply();
        prefs.edit().remove("user_email").apply();
        
        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    private List<Incident> createMockIncidents() {
        List<Incident> list = new ArrayList<>();
        list.add(new Incident("Minor accident", "Vehicle: KA-01-AB-1234 · 6 mins ago", "Open"));
        list.add(new Incident("Harsh braking", "Vehicle: MH-12-CD-9876 · 30 mins ago", "Resolved"));
        list.add(new Incident("Overspeed alert", "Vehicle: KA-05-XY-5566 · 2 hrs ago", "Open"));
        return list;
    }

    private void checkLocationPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void startLocationUpdates() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create()
                .setInterval(5000)
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    sendLocationToBackend(currentLocation.getLatitude(), currentLocation.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
    }

    private void sendLocationToBackend(double lat, double lng) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("lat", lat);
        body.addProperty("lang", lng);

        com.example.smartfleetx.network.RetrofitClient.getApiService().updatePhoneLocation(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<com.google.gson.JsonObject> call, @NonNull retrofit2.Response<com.google.gson.JsonObject> response) {
                if (response.isSuccessful()) {
                    android.util.Log.d("DashboardLocationSync", "Phone location successfully synced to backend: " + lat + ", " + lng);
                } else {
                    android.util.Log.e("DashboardLocationSync", "Failed to sync phone location: " + response.message());
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<com.google.gson.JsonObject> call, @NonNull Throwable t) {
                android.util.Log.e("DashboardLocationSync", "Error syncing phone location: " + t.getMessage());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
