package com.example.smartfleetx.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfleetx.R;
import com.example.smartfleetx.database.DatabaseHelper;
import com.example.smartfleetx.service.EmergencyAlertManager;
import com.example.smartfleetx.service.EmergencyAlertManager.EmergencyContact;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone, tvAccountCreated,
                     tvVehicleNumber, tvRcNumber, tvEngineNumber, tvLicenseNumber;
    private Button btnEditProfile, btnChangePassword, btnLogout, btnDeleteAccount, btnEmergencyContacts;
    
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private static final String PREFS_NAME = "app_prefs";
    private static final String EMERGENCY_PREFS = "EmergencyContacts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);

        initializeViews();
        loadUserData();
        setupListeners();
    }

    private void initializeViews() {
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvVehicleNumber = findViewById(R.id.tvVehicleNumber);
        tvRcNumber = findViewById(R.id.tvRcNumber);
        tvEngineNumber = findViewById(R.id.tvEngineNumber);
        tvLicenseNumber = findViewById(R.id.tvLicenseNumber);
        tvAccountCreated = findViewById(R.id.tvAccountCreated);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnEmergencyContacts = findViewById(R.id.btnEmergencyContacts);
        btnLogout = findViewById(R.id.btnLogout);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
    }

    private void loadUserData() {
        String email = prefs.getString("user_email", "user@fleet.com");
        String savedName = prefs.getString("user_name", "");
        String savedPhone = prefs.getString("user_phone", "Not provided");
        String savedVehicle = prefs.getString("user_vehicle", "Not provided");
        String savedRc = prefs.getString("user_rc", "Not provided");
        String savedEngine = prefs.getString("user_engine", "Not provided");
        String savedLicense = prefs.getString("user_license", "Not provided");
        
        if (savedName.isEmpty()) {
            savedName = email.split("@")[0];
            savedName = savedName.substring(0, 1).toUpperCase() + savedName.substring(1);
        }
        
        tvName.setText(savedName);
        tvEmail.setText(email);
        tvPhone.setText(savedPhone);
        tvVehicleNumber.setText(savedVehicle);
        tvRcNumber.setText(savedRc);
        tvEngineNumber.setText(savedEngine);
        tvLicenseNumber.setText(savedLicense);
        tvAccountCreated.setText("Member since 2024");
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        btnEmergencyContacts.setOnClickListener(v -> showEmergencyContactsDialog());

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        btnLogout.setOnClickListener(v -> logout());

        btnDeleteAccount.setOnClickListener(v -> {
            Toast.makeText(this, "Account deletion requires admin approval", Toast.LENGTH_LONG).show();
        });
    }

    private void showEditProfileDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Profile");

        // Set up the input
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.EditText inputName = new android.widget.EditText(this);
        inputName.setHint("Name");
        inputName.setText(tvName.getText().toString());
        layout.addView(inputName);

        final android.widget.EditText inputPhone = new android.widget.EditText(this);
        inputPhone.setHint("Phone Number");
        inputPhone.setText(tvPhone.getText().equals("Not provided") ? "" : tvPhone.getText().toString());
        inputPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputPhone);

        final android.widget.EditText inputVehicle = new android.widget.EditText(this);
        inputVehicle.setHint("Vehicle Number (Plate)");
        inputVehicle.setText(tvVehicleNumber.getText().equals("Not provided") ? "" : tvVehicleNumber.getText().toString());
        layout.addView(inputVehicle);

        final android.widget.EditText inputRc = new android.widget.EditText(this);
        inputRc.setHint("RC Number");
        inputRc.setText(tvRcNumber.getText().equals("Not provided") ? "" : tvRcNumber.getText().toString());
        layout.addView(inputRc);

        final android.widget.EditText inputEngine = new android.widget.EditText(this);
        inputEngine.setHint("Engine Number");
        inputEngine.setText(tvEngineNumber.getText().equals("Not provided") ? "" : tvEngineNumber.getText().toString());
        layout.addView(inputEngine);

        final android.widget.EditText inputLicense = new android.widget.EditText(this);
        inputLicense.setHint("License Number");
        inputLicense.setText(tvLicenseNumber.getText().equals("Not provided") ? "" : tvLicenseNumber.getText().toString());
        layout.addView(inputLicense);

        builder.setView(layout);

        // Set up the buttons
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = inputName.getText().toString().trim();
            String newPhone = inputPhone.getText().toString().trim();
            String newVehicle = inputVehicle.getText().toString().trim();
            String newRc = inputRc.getText().toString().trim();
            String newEngine = inputEngine.getText().toString().trim();
            String newLicense = inputLicense.getText().toString().trim();
            
            updateProfile(newName, newPhone, newVehicle, newRc, newEngine, newLicense);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateProfile(String name, String phone, String vehicle, String rc, String engine, String license) {
        String email = prefs.getString("user_email", "");
        if (email.isEmpty()) return;

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("email", email);
        if (!name.isEmpty()) body.addProperty("name", name);
        if (!phone.isEmpty()) body.addProperty("phone", phone);
        body.addProperty("vehicleNumber", vehicle);
        body.addProperty("rcNumber", rc);
        body.addProperty("engineNumber", engine);
        body.addProperty("licenseNumber", license);

        com.example.smartfleetx.network.ApiService apiService = com.example.smartfleetx.network.RetrofitClient.getApiService();
        apiService.updateProfile(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    // Update SharedPreferences
                    prefs.edit().putString("user_name", name).apply();
                    prefs.edit().putString("user_phone", phone).apply();
                    prefs.edit().putString("user_vehicle", vehicle).apply();
                    prefs.edit().putString("user_rc", rc).apply();
                    prefs.edit().putString("user_engine", engine).apply();
                    prefs.edit().putString("user_license", license).apply();
                    // Update UI
                    tvName.setText(name);
                    tvPhone.setText(phone.isEmpty() ? "Not provided" : phone);
                    tvVehicleNumber.setText(vehicle.isEmpty() ? "Not provided" : vehicle);
                    tvRcNumber.setText(rc.isEmpty() ? "Not provided" : rc);
                    tvEngineNumber.setText(engine.isEmpty() ? "Not provided" : engine);
                    tvLicenseNumber.setText(license.isEmpty() ? "Not provided" : license);
                } else {
                    Toast.makeText(ProfileActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Full network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showChangePasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Change Password");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputCurrent = new EditText(this);
        inputCurrent.setHint("Current Password");
        inputCurrent.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputCurrent);

        final EditText inputNew = new EditText(this);
        inputNew.setHint("New Password");
        inputNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputNew);

        final EditText inputConfirm = new EditText(this);
        inputConfirm.setHint("Confirm New Password");
        inputConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputConfirm);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String current = inputCurrent.getText().toString();
            String newer = inputNew.getText().toString();
            String confirm = inputConfirm.getText().toString();

            if (current.isEmpty() || newer.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newer.equals(confirm)) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (newer.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            performChangePassword(current, newer);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performChangePassword(String current, String newer) {
        String email = prefs.getString("user_email", "");
        if (email.isEmpty()) return;

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("email", email);
        body.addProperty("currentPassword", current);
        body.addProperty("newPassword", newer);

        com.example.smartfleetx.network.ApiService apiService = com.example.smartfleetx.network.RetrofitClient.getApiService();
        apiService.changePassword(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ProfileActivity.this, "Password changed successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ProfileActivity.this, "Failed: Check current password", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmergencyContactsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Contacts");

        // View for list
        View view = getLayoutInflater().inflate(android.R.layout.list_content, null); // Using default or programmatic
        // Ideally we need a custom layout, but for simplicity let's use a programmatic LinearLayout with ListView 
        // Or better, just a simple list of strings for now, but we want to Add.
        
        // Let's create a custom layout programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        ListView listView = new ListView(this);
        List<EmergencyContact> contacts = loadContacts();
        List<String> displayList = new ArrayList<>();
        for (EmergencyContact c : contacts) {
            displayList.add(c.getName() + " (" + c.getPriority() + ")\n" + c.getPhoneNumber());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listView.setAdapter(adapter);
        layout.addView(listView);

        // Add Button
        Button btnAdd = new Button(this);
        btnAdd.setText("Add New Contact");
        btnAdd.setOnClickListener(v -> {
            // Close current dialog? Or stack? Stacking is fine.
            showAddContactDialog(contacts, adapter, displayList);
        });
        layout.addView(btnAdd);
        
        // Delete on click
        listView.setOnItemLongClickListener((parent, view1, position, id) -> {
            new AlertDialog.Builder(ProfileActivity.this)
                .setTitle("Delete Contact")
                .setMessage("Remove " + contacts.get(position).getName() + "?")
                .setPositiveButton("Yes", (d, w) -> {
                    contacts.remove(position);
                    saveContacts(contacts);
                    displayList.remove(position);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("No", null)
                .show();
            return true;
        });

        builder.setView(layout);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showAddContactDialog(List<EmergencyContact> contacts, ArrayAdapter<String> adapter, List<String> displayList) {
        if (contacts.size() >= 5) {
            Toast.makeText(this, "Maximum 5 contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Contact");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        final EditText inputName = new EditText(this);
        inputName.setHint("Name");
        layout.addView(inputName);

        final EditText inputPhone = new EditText(this);
        inputPhone.setHint("Phone Number");
        inputPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputPhone);

        // Priority Spinner could be added here, defaults to PRIMARY for now
        
        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String phone = inputPhone.getText().toString().trim();
            
            if (!name.isEmpty() && !phone.isEmpty()) {
                // Regex Validation: Validates for 10-digit phone numbers
                if (!phone.matches("^[6-9][0-9]{9}$")) {
                    Toast.makeText(ProfileActivity.this, "Enter a valid 10-digit Indian mobile number", Toast.LENGTH_LONG).show();
                    return;
                }

                EmergencyContact newContact = new EmergencyContact(name, phone, "PRIMARY");
                contacts.add(newContact);
                saveContacts(contacts);
                
                displayList.add(name + " (PRIMARY)\n" + phone);
                adapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private List<EmergencyContact> loadContacts() {
        SharedPreferences ep = getSharedPreferences(EMERGENCY_PREFS, MODE_PRIVATE);
        String json = ep.getString("contacts", null);
        if (json != null) {
            return new Gson().fromJson(json, new TypeToken<List<EmergencyContact>>(){}.getType());
        }
        return new ArrayList<>();
    }

    private void saveContacts(List<EmergencyContact> contacts) {
        SharedPreferences ep = getSharedPreferences(EMERGENCY_PREFS, MODE_PRIVATE);
        ep.edit().putString("contacts", new Gson().toJson(contacts)).apply();
    }

    private void logout() {
        prefs.edit().putBoolean("is_logged_in", false).apply();
        prefs.edit().remove("user_email").apply();
        
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }
}
