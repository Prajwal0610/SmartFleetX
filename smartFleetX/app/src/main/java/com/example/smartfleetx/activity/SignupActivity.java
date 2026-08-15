package com.example.smartfleetx.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfleetx.R;
import com.example.smartfleetx.network.ApiService;
import com.example.smartfleetx.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirmPassword;
    private Button btnSignup;
    private TextView tvLogin;
    private ProgressBar progress;
    
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_EMAIL = "saved_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone); // Init phone
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvLogin = findViewById(R.id.tvLogin);
        progress = findViewById(R.id.progress);

        btnSignup.setOnClickListener(v -> {
            animateButton(v);
            attemptSignup();
        });

        tvLogin.setOnClickListener(v -> {
            finish(); // Go back to Login
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        });
    }

    private void animateButton(View v) {
        ScaleAnimation sa = new ScaleAnimation(
                1f, 0.98f,
                1f, 0.98f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        );
        sa.setDuration(120);
        sa.setInterpolator(new AccelerateDecelerateInterpolator());
        sa.setRepeatCount(1);
        sa.setRepeatMode(ScaleAnimation.REVERSE);
        v.startAnimation(sa);
    }

    private void attemptSignup() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        if (name.isEmpty()) {
            etName.setError("Name required");
            return;
        }

        if (!isValidEmail(email)) {
            etEmail.setError("Valid email required");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Min 6 chars");
            return;
        }

        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnSignup.setEnabled(false);

        // JSON Body
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("email", email);
        body.addProperty("password", password);
        
        // Add phone number (optional)
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        body.addProperty("phone", phone);

        ApiService apiService = RetrofitClient.getApiService();
        apiService.register(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progress.setVisibility(View.GONE);
                btnSignup.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                   Toast.makeText(SignupActivity.this, "Account created!", Toast.LENGTH_SHORT).show();
                   
                   // Auto login
                   prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                   prefs.edit().putString(KEY_EMAIL, email).apply();
                   prefs.edit().putString("user_email", email).apply(); // For Dashboard
                   prefs.edit().putString("user_name", name).apply();
                   prefs.edit().putString("user_phone", phone).apply();

                   navigateToDashboard();
                } else {
                    Toast.makeText(SignupActivity.this, "Signup failed: Email might be taken", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progress.setVisibility(View.GONE);
                btnSignup.setEnabled(true);
                Toast.makeText(SignupActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isValidEmail(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void navigateToDashboard() {
        Intent i = new Intent(SignupActivity.this, DashboardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
