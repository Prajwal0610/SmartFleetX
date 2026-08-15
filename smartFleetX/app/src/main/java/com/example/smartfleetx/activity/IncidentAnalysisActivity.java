package com.example.smartfleetx.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfleetx.R;

import java.util.Random;

public class IncidentAnalysisActivity extends AppCompatActivity {

    private TextView tvFaultResult, tvConfidence, tvSpeedAnalysis, 
                     tvDriverAnalysis, tvVehicleAnalysis;
    private Button btnGenerateReport, btnInsuranceClaim, btnViewFootage;
    
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_analysis);

        initializeViews();
        setupListeners();
        performAnalysis();
    }

    private void initializeViews() {
        tvFaultResult = findViewById(R.id.tvFaultResult);
        tvConfidence = findViewById(R.id.tvConfidence);
        tvSpeedAnalysis = findViewById(R.id.tvSpeedAnalysis);
        tvDriverAnalysis = findViewById(R.id.tvDriverAnalysis);
        tvVehicleAnalysis = findViewById(R.id.tvVehicleAnalysis);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnInsuranceClaim = findViewById(R.id.btnInsuranceClaim);
        btnViewFootage = findViewById(R.id.btnViewFootage);
    }

    private void setupListeners() {
        btnGenerateReport.setOnClickListener(v -> generateReport());
        btnInsuranceClaim.setOnClickListener(v -> fileInsuranceClaim());
        btnViewFootage.setOnClickListener(v -> viewFootage());
    }

    private void performAnalysis() {
        Toast.makeText(this, "Analyzing incident data...", Toast.LENGTH_SHORT).show();
        
        // Simulate AI-powered analysis
        analyzeIncident();
    }

    private void analyzeIncident() {
        // Random fault determination for demo
        String[] faultTypes = {
            "NO FAULT DETECTED",
            "PARTIAL FAULT",
            "OTHER PARTY AT FAULT"
        };
        
        int faultIndex = random.nextInt(faultTypes.length);
        String faultResult = faultTypes[faultIndex];
        int confidence = 85 + random.nextInt(15); // 85-100%
        
        tvFaultResult.setText(faultResult);
        tvConfidence.setText("Confidence: " + confidence + "%");
        
        // Speed analysis
        int vehicleSpeed = 40 + random.nextInt(20); // 40-60 km/h
        int speedLimit = 60;
        boolean withinLimit = vehicleSpeed <= speedLimit;
        
        tvSpeedAnalysis.setText(
            "Vehicle speed: " + vehicleSpeed + " km/h\n" +
            "Speed limit: " + speedLimit + " km/h\n" +
            (withinLimit ? "✓ Within speed limit" : "⚠ Speeding detected")
        );
        
        // Driver analysis
        int attentionScore = 85 + random.nextInt(15); // 85-100
        tvDriverAnalysis.setText(
            "Attention Score: " + attentionScore + "/100\n" +
            "Drowsiness: No\n" +
            "Distraction: No\n" +
            "✓ Driver alert and attentive"
        );
        
        // Vehicle analysis
        tvVehicleAnalysis.setText(
            "Hard brake detected: No\n" +
            "Rapid acceleration: No\n" +
            "DTC codes: None\n" +
            "✓ Normal vehicle operation"
        );
    }

    private void generateReport() {
        if (checkStoragePermission()) {
            try {
                createPdf();
            } catch (Exception e) {
                Toast.makeText(this, "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            requestStoragePermission();
        }
    }
    
    // Simple permission logic for demo purposes
    private boolean checkStoragePermission() {
        // For Android 10+ scoped storage, we don't strictly need WRITE_EXTERNAL for app-specific dirs
        // But for public Downloads, we use MediaStore or check permission for older versions
        return true; 
    }
    
    private void requestStoragePermission() {
        // Implement real request logic if needed
        Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
    }

    private void createPdf() throws java.io.IOException {
        String fileName = "Incident_Report_" + System.currentTimeMillis() + ".pdf";
        java.io.File file = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), fileName);
        
        com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(file);
        com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);
        
        document.add(new com.itextpdf.layout.element.Paragraph("Incident Analysis Report")
                .setFontSize(24).setBold());
        document.add(new com.itextpdf.layout.element.Paragraph("Date: " + new java.util.Date().toString()));
        
        // Add Summary Section
        document.add(new com.itextpdf.layout.element.Paragraph("\nExecutive Summary").setBold().setFontSize(14));
        String summaryText = String.format(
            "Based on AI-driven analysis, the incident has been classified as: %s with a confidence level of %s. " +
            "The system records indicate %s. Vehicle telemetry suggests %s.",
            tvFaultResult.getText(),
            tvConfidence.getText(),
            tvDriverAnalysis.getText().toString().split("\n")[3].replace("✓ ", ""),
            tvVehicleAnalysis.getText().toString().split("\n")[3].replace("✓ ", "")
        );
        document.add(new com.itextpdf.layout.element.Paragraph(summaryText).setItalic());

        document.add(new com.itextpdf.layout.element.Paragraph("\nFault Analysis: " + tvFaultResult.getText()));
        document.add(new com.itextpdf.layout.element.Paragraph(tvConfidence.getText().toString()));
        document.add(new com.itextpdf.layout.element.Paragraph("\nDetails:"));
        document.add(new com.itextpdf.layout.element.Paragraph(tvSpeedAnalysis.getText().toString()));
        document.add(new com.itextpdf.layout.element.Paragraph(tvDriverAnalysis.getText().toString()));
        document.add(new com.itextpdf.layout.element.Paragraph(tvVehicleAnalysis.getText().toString()));
        
        // Add Watermark
        com.itextpdf.kernel.font.PdfFont font = com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        com.itextpdf.layout.element.Paragraph watermark = new com.itextpdf.layout.element.Paragraph("Smart FleetX")
                .setFont(font)
                .setFontSize(60)
                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
                .setOpacity(0.3f);
        
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            com.itextpdf.kernel.geom.Rectangle pageSize = pdf.getPage(i).getPageSize();
            float x = pageSize.getWidth() / 2;
            float y = pageSize.getHeight() / 2;
            document.showTextAligned(watermark, x, y, i, com.itextpdf.layout.properties.TextAlignment.CENTER, com.itextpdf.layout.properties.VerticalAlignment.MIDDLE, (float) Math.toRadians(70));
        }
        
        document.close();
        
        Toast.makeText(this, "PDF Saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        
        // Share option
        sharePdf(file);
    }
    
    private void sharePdf(java.io.File file) {
        // Basic share intent
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".provider", file);
                
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Report"));
    }

    private void fileInsuranceClaim() {
        Toast.makeText(this, "Filing insurance claim...", Toast.LENGTH_SHORT).show();
        // Send SMS notification
        sendSmsNotification();
        
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(this, "Claim filed successfully - Ref: IC" + 
                (1000 + random.nextInt(9000)), Toast.LENGTH_LONG).show();
        }, 1500);
    }
    
    private void sendSmsNotification() {
        // Send SMS intent
        String message = "SmartFleetX Alert: Incident report generated and insurance claim filed. Ref: REF" + System.currentTimeMillis();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse("sms:"));
        intent.putExtra("sms_body", message);
        startActivity(intent);
    }

    private void viewFootage() {
        Toast.makeText(this, "Opening video player...", Toast.LENGTH_SHORT).show();
        // Could navigate to VideoPlaybackActivity
        startActivity(new Intent(this, VideoPlaybackActivity.class));
    }
}
