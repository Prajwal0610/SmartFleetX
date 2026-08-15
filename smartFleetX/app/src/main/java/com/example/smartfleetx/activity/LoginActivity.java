package com.example.smartfleetx.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.CheckBox;
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

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progress;
    private CheckBox cbRemember;
    private TextView tvForgot, tvSignUp;

    private SharedPreferences prefs;
    
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_REMEMBER = "remember_me";
    private static final String KEY_EMAIL = "saved_email";
    private static final String KEY_LOGGED_IN = "is_logged_in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Check if already logged in
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            navigateToDashboard();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.progress);
        cbRemember = findViewById(R.id.cbRemember);
        tvForgot = findViewById(R.id.tvForgot);
        tvSignUp = findViewById(R.id.tvSignUp);

        loadSavedPrefs();

        btnLogin.setOnClickListener(v -> {
            animateButton(v);
            attemptLogin();
        });

        tvForgot.setOnClickListener(v -> {
            Toast.makeText(this, "Contact admin to reset password", Toast.LENGTH_SHORT).show();
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
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

    private void loadSavedPrefs() {
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);
        if (remember) {
            cbRemember.setChecked(true);
            String savedEmail = prefs.getString(KEY_EMAIL, "");
            etEmail.setText(savedEmail);
        }
    }

    private void attemptLogin() {
        etEmail.setError(null);
        etPassword.setError(null);

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (!isValidEmail(email)) {
            etEmail.setError("Enter a valid email");
            etEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // Prepare JSON Body
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        // API Call
        ApiService apiService = RetrofitClient.getApiService();
        apiService.login(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body().getAsJsonObject("data");
                    String userEmail = data.get("email").getAsString();
                    // You can also save userId or token here

                    savePrefsIfNeeded(userEmail);
                    prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                    prefs.edit().putString("user_email", userEmail).apply();
                    
                    // Save name and phone from API response
                    if (data.has("name") && !data.get("name").isJsonNull()) {
                        prefs.edit().putString("user_name", data.get("name").getAsString()).apply();
                    }
                    if (data.has("phone") && !data.get("phone").isJsonNull()) {
                        prefs.edit().putString("user_phone", data.get("phone").getAsString()).apply();
                    }
                    if (data.has("vehicleNumber") && !data.get("vehicleNumber").isJsonNull()) {
                        prefs.edit().putString("user_vehicle", data.get("vehicleNumber").getAsString()).apply();
                    }
                    if (data.has("rcNumber") && !data.get("rcNumber").isJsonNull()) {
                        prefs.edit().putString("user_rc", data.get("rcNumber").getAsString()).apply();
                    }
                    if (data.has("engineNumber") && !data.get("engineNumber").isJsonNull()) {
                        prefs.edit().putString("user_engine", data.get("engineNumber").getAsString()).apply();
                    }
                    if (data.has("licenseNumber") && !data.get("licenseNumber").isJsonNull()) {
                        prefs.edit().putString("user_license", data.get("licenseNumber").getAsString()).apply();
                    }
                    
                    navigateToDashboard();
                } else {
                    Toast.makeText(LoginActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isValidEmail(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void savePrefsIfNeeded(String email) {
        SharedPreferences.Editor editor = prefs.edit();
        if (cbRemember.isChecked()) {
            editor.putBoolean(KEY_REMEMBER, true);
            editor.putString(KEY_EMAIL, email);
        } else {
            editor.putBoolean(KEY_REMEMBER, false);
            editor.remove(KEY_EMAIL);
        }
        editor.apply();
    }

    private void navigateToDashboard() {
        Intent i = new Intent(LoginActivity.this, DashboardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
