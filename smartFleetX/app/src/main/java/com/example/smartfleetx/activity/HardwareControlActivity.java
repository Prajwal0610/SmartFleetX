package com.example.smartfleetx.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfleetx.R;
import com.example.smartfleetx.network.ApiService;
import com.example.smartfleetx.network.RetrofitClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HardwareControlActivity extends AppCompatActivity {

    private Button btnRelay1, btnRelay2;
    private SwitchMaterial switchRelay3, switchRelay4;
    private ApiService apiService;

    // Track state to avoid sending too many redundant network requests on touch
    private int relay1State = 0;
    private int relay2State = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_control);

        apiService = RetrofitClient.getApiService();

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        topAppBar.setNavigationOnClickListener(v -> finish());

        btnRelay1 = findViewById(R.id.btnRelay1);
        btnRelay2 = findViewById(R.id.btnRelay2);
        switchRelay3 = findViewById(R.id.switchRelay3);
        switchRelay4 = findViewById(R.id.switchRelay4);

        setupPushButtons();
        setupToggleSwitches();
        
        // Fetch current status on open
        fetchInitialStatus();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPushButtons() {
        // Relay 1 (Momentary ON when pressed)
        btnRelay1.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (relay1State != 1) {
                        relay1State = 1;
                        setRelayState(1, 1);
                        btnRelay1.setPressed(true);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (relay1State != 0) {
                        relay1State = 0;
                        setRelayState(1, 0);
                        btnRelay1.setPressed(false);
                    }
                    return true;
            }
            return false;
        });

        // Relay 2 (Momentary ON when pressed)
        btnRelay2.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (relay2State != 1) {
                        relay2State = 1;
                        setRelayState(2, 1);
                        btnRelay2.setPressed(true);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (relay2State != 0) {
                        relay2State = 0;
                        setRelayState(2, 0);
                        btnRelay2.setPressed(false);
                    }
                    return true;
            }
            return false;
        });
    }

    private void setupToggleSwitches() {
        // Relay 3 (Persistent Toggle)
        switchRelay3.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Only send API request if changed by user click
                setRelayState(3, isChecked ? 1 : 0);
            }
        });

        // Relay 4 (Persistent Toggle)
        switchRelay4.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                setRelayState(4, isChecked ? 1 : 0);
            }
        });
    }

    private void setRelayState(int relayNumber, int state) {
        Call<JsonObject> call;
        switch (relayNumber) {
            case 1: call = apiService.toggleRelay1(state); break;
            case 2: call = apiService.toggleRelay2(state); break;
            case 3: call = apiService.toggleRelay3(state); break;
            case 4: call = apiService.toggleRelay4(state); break;
            default: return;
        }

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(HardwareControlActivity.this, "Failed to set Relay " + relayNumber, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(HardwareControlActivity.this, "Network error: Relay " + relayNumber, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchInitialStatus() {
        apiService.getRelayStatus().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();
                    
                    if (data.has("relay3")) {
                        switchRelay3.setChecked(data.get("relay3").getAsInt() == 1);
                    }
                    if (data.has("relay4")) {
                        switchRelay4.setChecked(data.get("relay4").getAsInt() == 1);
                    }
                    // Ignoring Relay 1 & 2 for persistent state display, as they are momentary push buttons
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(HardwareControlActivity.this, "Failed to load current hardware stats", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
