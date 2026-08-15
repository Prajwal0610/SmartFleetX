package com.example.smartfleetx.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfleetx.R;
import com.example.smartfleetx.network.ApiService;
import com.example.smartfleetx.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DataSharingActivity extends AppCompatActivity {

    private TextInputEditText etRecipientEmail;
    private Spinner spinnerRole, spinnerDuration;
    private Button btnGrantAccess;
    private RecyclerView rvActiveShares;

    private String incidentId;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_sharing);

        // Get Incident ID from intent (Mock for now if null)
        incidentId = getIntent().getStringExtra("incident_id");
        if (incidentId == null) incidentId = "INC-MOCK-123";

        apiService = RetrofitClient.getApiService();

        initializeViews();
        setupSpinners();
        setupListeners();
        loadActiveShares();
    }

    private void initializeViews() {
        etRecipientEmail = findViewById(R.id.etRecipientEmail);
        spinnerRole = findViewById(R.id.spinnerRole);
        spinnerDuration = findViewById(R.id.spinnerDuration);
        btnGrantAccess = findViewById(R.id.btnGrantAccess);
        rvActiveShares = findViewById(R.id.rvActiveShares);
        rvActiveShares.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupSpinners() {
        // Mock Roles
        String[] roles = {"INSURANCE", "AUTHORITY", "FAMILY", "LEGAL"};
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        // Mock Durations
        String[] durations = {"24 Hours", "48 Hours", "7 Days", "30 Days"};
        ArrayAdapter<String> durationAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, durations);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDuration.setAdapter(durationAdapter);
    }

    private void setupListeners() {
        btnGrantAccess.setOnClickListener(v -> grantAccess());
    }

    private void grantAccess() {
        String email = etRecipientEmail.getText() != null ? etRecipientEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            etRecipientEmail.setError("Email required");
            return;
        }

        String role = spinnerRole.getSelectedItem().toString();
        int durationIndex = spinnerDuration.getSelectedItemPosition();
        int hours = (durationIndex == 0) ? 24 : (durationIndex == 1) ? 48 : (durationIndex == 2) ? 168 : 720;

        JsonObject body = new JsonObject();
        body.addProperty("incidentId", incidentId);
        body.addProperty("grantedToEmail", email);
        body.addProperty("role", role);
        body.addProperty("expiresInHours", hours);

        // Call API
        apiService.grantAccess(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(DataSharingActivity.this, "Access Granted & Link Generated", Toast.LENGTH_LONG).show();
                    // Copy link to clipboard (simulation)
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Access Link", "https://smartfleetx.com/access?token=MOCK_TOKEN");
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(DataSharingActivity.this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                    
                    loadActiveShares(); // Refresh list
                } else {
                    Toast.makeText(DataSharingActivity.this, "Failed to grant access", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(DataSharingActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadActiveShares() {
        // Implement API call to get active shares and populate RecyclerView
        // For now, leaving empty or mock
    }
}
