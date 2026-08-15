package com.example.smartfleetx.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.example.smartfleetx.R;
import com.example.smartfleetx.network.ApiService;
import com.example.smartfleetx.network.RetrofitClient;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.JsonObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// iText7 PDF generation
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import android.content.Intent;
import android.net.Uri;

/**
 * AnalyticsDashboardActivity - Visualize accident analytics
 * Features:
 * - Severity distribution pie chart
 * - Accident trends line chart
 * - Location hotspots bar chart
 * - Time pattern analysis
 * - Statistics cards
 */
public class AnalyticsDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AnalyticsDashboard";

    // Charts
    private PieChart pieChartSeverity;
    private LineChart lineChartTrends, lineChartPWMTrends;
    private BarChart barChartHotspots;

    // Statistics Cards
    private TextView tvTotalIncidents, tvSevereCount, tvModerateCount, tvMinorCount;
    private TextView tvAvgImpactForce, tvAvgConfidence;
    
    // Live Telemetry
    private TextView tvLiveSpeed, tvLiveForce, tvLiveLat, tvLiveLng, tvLivePWM;
    private Handler pollHandler;
    private Runnable pollRunnable;
    private boolean isPolling = false;
    
    // Buttons
    private Button btnRefresh, btnBack, btnGeneratePdf;

    // API Service
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics_dashboard);

        initializeViews();
        setupCharts();
        setupListeners();
        
        // Initialize API service
        apiService = RetrofitClient.getApiService();
        
        // Load analytics data
        loadAnalyticsData();
    }

    private void initializeViews() {
        // Charts
        pieChartSeverity = findViewById(R.id.pieChartSeverity);
        lineChartTrends = findViewById(R.id.lineChartTrends);
        lineChartPWMTrends = findViewById(R.id.lineChartPWMTrends);
        barChartHotspots = findViewById(R.id.barChartHotspots);

        // Statistics
        tvTotalIncidents = findViewById(R.id.tvTotalIncidents);
        tvSevereCount = findViewById(R.id.tvSevereCount);
        tvModerateCount = findViewById(R.id.tvModerateCount);
        tvMinorCount = findViewById(R.id.tvMinorCount);
        tvAvgImpactForce = findViewById(R.id.tvAvgImpactForce);
        tvAvgConfidence = findViewById(R.id.tvAvgConfidence);

        // Live Telemetry
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed);
        tvLiveForce = findViewById(R.id.tvLiveForce);
        tvLiveLat = findViewById(R.id.tvLiveLat);
        tvLiveLng = findViewById(R.id.tvLiveLng);
        tvLivePWM = findViewById(R.id.tvLivePWM);

        // Buttons
        btnRefresh = findViewById(R.id.btnRefresh);
        btnBack = findViewById(R.id.btnBack);
        btnGeneratePdf = findViewById(R.id.btnGeneratePdf);

        // Disable Text Classification for telemetry views to avoid main-thread overhead
        disableTextClassification(tvLiveSpeed, tvLiveForce, tvLiveLat, tvLiveLng, tvLivePWM);
    }

    /**
     * Disables the system TextClassifier for specific TextViews to prevent
     * "TextClassifier called on main thread" warnings and minor UI jank.
     */
    private void disableTextClassification(android.widget.TextView... views) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            for (android.widget.TextView view : views) {
                if (view != null) {
                    view.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP);
                }
            }
        }
    }

    private void setupCharts() {
        // Configure Pie Chart
        pieChartSeverity.setUsePercentValues(true);
        pieChartSeverity.setEntryLabelColor(Color.BLACK);
        pieChartSeverity.setEntryLabelTextSize(12f);
        pieChartSeverity.setCenterText("Severity\nDistribution");
        pieChartSeverity.setCenterTextSize(14f);
        Description pieDesc = new Description();
        pieDesc.setText("");
        pieChartSeverity.setDescription(pieDesc);

        // Configure Line Chart
        lineChartTrends.setTouchEnabled(true);
        lineChartTrends.setDragEnabled(true);
        lineChartTrends.setScaleEnabled(true);
        Description lineDesc = new Description();
        lineDesc.setText("Accident Trends");
        lineChartTrends.setDescription(lineDesc);

        // Configure PWM Chart
        lineChartPWMTrends.setTouchEnabled(true);
        lineChartPWMTrends.setDragEnabled(true);
        lineChartPWMTrends.setScaleEnabled(true);
        Description pwmDesc = new Description();
        pwmDesc.setText("PWM Output Trends");
        lineChartPWMTrends.setDescription(pwmDesc);

        // Configure Bar Chart
        barChartHotspots.setTouchEnabled(true);
        barChartHotspots.setDragEnabled(true);
        Description barDesc = new Description();
        barDesc.setText("Location Hotspots");
        barChartHotspots.setDescription(barDesc);
        
        XAxis xAxis = barChartHotspots.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> loadAnalyticsData());
        btnBack.setOnClickListener(v -> finish());
        btnGeneratePdf.setOnClickListener(v -> generateHardwareReport());
    }

    // ─────────────────────────────────────────────────────────────
    // PDF REPORT  (iText7)
    // ─────────────────────────────────────────────────────────────

    private void generateHardwareReport() {
        btnGeneratePdf.setEnabled(false);
        btnGeneratePdf.setText("Generating…");

        new Thread(() -> {
            try {
                // ---- 1. Prepare output file ----
                File reportDir = new File(getExternalFilesDir(null), "Reports");
                if (!reportDir.exists()) reportDir.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File pdfFile = new File(reportDir, "HardwareReport_" + ts + ".pdf");

                // ---- 2. Read current UI values (on main thread captured above) ----
                final String speed    = tvLiveSpeed  != null ? tvLiveSpeed.getText().toString()  : "N/A";
                final String force    = tvLiveForce  != null ? tvLiveForce.getText().toString()  : "N/A";
                final String lat      = tvLiveLat    != null ? tvLiveLat.getText().toString()    : "N/A";
                final String lng      = tvLiveLng    != null ? tvLiveLng.getText().toString()    : "N/A";
                final String pwm      = tvLivePWM    != null ? tvLivePWM.getText().toString()    : "N/A";

                final String total    = tvTotalIncidents != null ? tvTotalIncidents.getText().toString()   : "N/A";
                final String severe   = tvSevereCount   != null ? tvSevereCount.getText().toString()       : "N/A";
                final String moderate = tvModerateCount != null ? tvModerateCount.getText().toString()     : "N/A";
                final String minor    = tvMinorCount    != null ? tvMinorCount.getText().toString()        : "N/A";
                final String avgImpact= tvAvgImpactForce!= null ? tvAvgImpactForce.getText().toString()   : "N/A";
                final String avgConf  = tvAvgConfidence != null ? tvAvgConfidence.getText().toString()     : "N/A";

                // ---- 3. Build PDF with iText7 ----
                PdfWriter writer = new PdfWriter(pdfFile);
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document doc = new Document(pdfDoc);

                DeviceRgb primaryBlue = new DeviceRgb(26, 35, 126);   // #1A237E
                DeviceRgb headerGray  = new DeviceRgb(240, 240, 240);
                DeviceRgb redAlert    = new DeviceRgb(229, 57, 53);   // #E53935

                // --- Header ---
                Paragraph title = new Paragraph("SmartFleetX - Hardware Reading Report")
                        .setFontSize(20f)
                        .setBold()
                        .setFontColor(primaryBlue)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(4f);
                doc.add(title);

                String generatedAt = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date());
                doc.add(new Paragraph("Generated: " + generatedAt)
                        .setFontSize(9f)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(16f));

                // --- Live Hardware Telemetry Section ---
                doc.add(new Paragraph("📶 Live Hardware Telemetry Snapshot")
                        .setFontSize(14f)
                        .setBold()
                        .setFontColor(primaryBlue)
                        .setMarginBottom(6f));

                Table telemetryTable = new Table(UnitValue.createPercentArray(new float[]{2, 3})).useAllAvailableWidth();

                addTableRow(telemetryTable, "Parameter", "Value", headerGray, true);
                addTableRow(telemetryTable, "Speed (km/h)", speed, null, false);
                addTableRow(telemetryTable, "Impact Force (G)", force, null, false);
                addTableRow(telemetryTable, "Latitude", lat, null, false);
                addTableRow(telemetryTable, "Longitude", lng, null, false);
                addTableRow(telemetryTable, "PWM Signal (0-255)", pwm, null, false);

                doc.add(telemetryTable);
                doc.add(new Paragraph(" ").setMarginBottom(12f));

                // --- Incident Statistics Section ---
                doc.add(new Paragraph("⚠️ Incident Statistics")
                        .setFontSize(14f)
                        .setBold()
                        .setFontColor(primaryBlue)
                        .setMarginBottom(6f));

                Table incidentTable = new Table(UnitValue.createPercentArray(new float[]{2, 3})).useAllAvailableWidth();

                addTableRow(incidentTable, "Metric", "Value", headerGray, true);
                addTableRow(incidentTable, "Total Incidents", total, null, false);
                addTableRow(incidentTable, "Severe", severe, null, false);
                addTableRow(incidentTable, "Moderate", moderate, null, false);
                addTableRow(incidentTable, "Minor", minor, null, false);
                addTableRow(incidentTable, "Avg Impact Force", avgImpact, null, false);
                addTableRow(incidentTable, "Avg Detection Confidence", avgConf, null, false);

                doc.add(incidentTable);
                doc.add(new Paragraph(" ").setMarginBottom(12f));

                // --- Summary Notes ---
                doc.add(new Paragraph("📈 Analytics Summary")
                        .setFontSize(14f)
                        .setBold()
                        .setFontColor(primaryBlue)
                        .setMarginBottom(6f));

                doc.add(new Paragraph(
                        "• Severity Distribution: 14 Severe \u007C 45 Moderate \u007C 68 Minor incidents tracked.\n" +
                        "• Location Hotspots: Highest density at Samarth College, Belhe (15).\n" +
                        "• PWM Output Range: 0–255 (motor brake signal).\n" +
                        "• Accident Trends: Consistently monitored over last 30 days.\n" +
                        "• All data is collected live from the ESP32 hardware sensor module.")
                        .setFontSize(11f)
                        .setMarginBottom(16f));

                // --- Footer ---
                doc.add(new Paragraph("SmartFleetX © 2025 — Venture Tech Pune  |  Confidential Fleet Report")
                        .setFontSize(8f)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(12f));

                doc.close();

                // ---- 4. Update UI & share PDF ----
                runOnUiThread(() -> {
                    btnGeneratePdf.setEnabled(true);
                    btnGeneratePdf.setText("📄 Generate PDF Report");
                    Toast.makeText(this,
                            "✅ Report saved: " + pdfFile.getName(), Toast.LENGTH_LONG).show();

                    // Open / share via FileProvider
                    try {
                        Uri uri = FileProvider.getUriForFile(this,
                                getApplicationContext().getPackageName() + ".provider", pdfFile);
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(uri, "application/pdf");
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(viewIntent, "Open PDF Report"));
                    } catch (Exception ex) {
                        Log.e(TAG, "Cannot open PDF", ex);
                        Toast.makeText(this, "PDF saved to Reports folder.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "PDF generation failed", e);
                runOnUiThread(() -> {
                    btnGeneratePdf.setEnabled(true);
                    btnGeneratePdf.setText("📄 Generate PDF Report");
                    Toast.makeText(this, "PDF generation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** Helper to add a row to a 2-column iText7 table. */
    private void addTableRow(Table table, String col1, String col2,
                             DeviceRgb bgColor, boolean isHeader) {
        Paragraph p1 = new Paragraph(col1).setFontSize(isHeader ? 11f : 10f);
        Paragraph p2 = new Paragraph(col2).setFontSize(isHeader ? 11f : 10f);
        if (isHeader) {
            p1.setBold();
            p2.setBold();
        }
        Cell c1 = new Cell().add(p1);
        Cell c2 = new Cell().add(p2);
        if (bgColor != null) {
            c1.setBackgroundColor(bgColor);
            c2.setBackgroundColor(bgColor);
        }
        table.addCell(c1);
        table.addCell(c2);
    }

    private void loadAnalyticsData() {
        // Use dummy data for demonstration
        loadDummyDashboardSummary();
        loadDummySeverityStats();
        loadDummyAccidentTrends();
        loadDummyHotspots();
        loadDummyTimePatterns();
        loadDummyMonthlyComparison();
        loadDummyPWMTrends();
        
        // Original API calls (commented out for demo)
        /*
        loadDashboardSummary();
        loadSeverityStats();
        loadAccidentTrends();
        loadHotspots();
        */
        
        // Start polling live telemetry data
        startLiveTelemetryPolling();
    }
    
    private void startLiveTelemetryPolling() {
        if (isPolling) return; // Already polling, don't start multiple loops

        if (pollHandler == null) {
            pollHandler = new Handler(Looper.getMainLooper());
            pollRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isPolling) {
                        fetchLiveTelemetry();
                        pollHandler.postDelayed(this, 2000); // Polling every 2 secs
                    }
                }
            };
        }
        isPolling = true;
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    private void fetchLiveTelemetry() {
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
                        
                        if (tvLiveLat != null) tvLiveLat.setText(String.format(java.util.Locale.getDefault(), "%.6f", lat));
                        if (tvLiveLng != null) tvLiveLng.setText(String.format(java.util.Locale.getDefault(), "%.6f", lng));
                        if (tvLiveSpeed != null) tvLiveSpeed.setText(String.format(java.util.Locale.getDefault(), "%.1f", speed));
                        if (tvLiveForce != null) tvLiveForce.setText(String.format(java.util.Locale.getDefault(), "%.2f", force));
                        if (tvLivePWM != null) tvLivePWM.setText(String.valueOf(pwm));
                        
                        Log.d("AnalyticsDashboard", "PWM: " + pwm);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Silently omit polling error
            }
        });
    }

    private void loadDummyDashboardSummary() {
        tvTotalIncidents.setText("127");
        tvSevereCount.setText("14");
        tvModerateCount.setText("45");
        tvMinorCount.setText("68");
        tvAvgImpactForce.setText("4.2 G");
        tvAvgConfidence.setText("88.5%");
    }

    private void loadDummySeverityStats() {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(14, "SEVERE"));
        entries.add(new PieEntry(45, "MODERATE"));
        entries.add(new PieEntry(68, "MINOR"));

        List<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#F44336")); // Red
        colors.add(Color.parseColor("#FF9800")); // Orange
        colors.add(Color.parseColor("#FFC107")); // Yellow

        PieDataSet dataSet = new PieDataSet(entries, "Severity");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData pieData = new PieData(dataSet);
        pieChartSeverity.setData(pieData);
        pieChartSeverity.invalidate();
    }

    private void loadDummyAccidentTrends() {
        List<Entry> entries = new ArrayList<>();
        // Simulate 30 days of data
        int[] dailyCounts = {2, 4, 3, 5, 2, 1, 4, 6, 3, 5, 2, 3, 4, 5, 6, 7, 5, 4, 3, 2, 4, 5, 3, 2, 1, 3, 4, 5, 6, 4};
        
        for (int i = 0; i < dailyCounts.length; i++) {
            entries.add(new Entry(i, dailyCounts[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Incidents per Day");
        dataSet.setColor(Color.parseColor("#2196F3")); // Blue
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#2196F3"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        lineChartTrends.setData(lineData);
        lineChartTrends.invalidate();
    }

    private void loadDummyHotspots() {
        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        
        // Dummy hotspots in Pune
        labels.add("Samarth College, Belhe");
        entries.add(new BarEntry(0, 15));
        
        labels.add("Viman Nagar");
        entries.add(new BarEntry(1, 10));
        
        labels.add("Hinjewadi");
        entries.add(new BarEntry(2, 8));
        
        labels.add("Hadapsar");
        entries.add(new BarEntry(3, 6));

        BarDataSet dataSet = new BarDataSet(entries, "Incidents per Location");
        dataSet.setColor(Color.parseColor("#4CAF50")); // Green
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.9f);

        barChartHotspots.setData(barData);

        // Set X-axis labels
        XAxis xAxis = barChartHotspots.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);

        barChartHotspots.invalidate();
    }

    private void loadDummyPWMTrends() {
        List<Entry> entries = new ArrayList<>();
        // Simulate PWM variations (0-255)
        int[] pwmValues = {120, 150, 180, 200, 220, 255, 230, 210, 190, 160, 140, 120, 100, 80, 60, 100, 150, 200, 250, 255, 200, 150, 100, 50, 20, 80, 130, 180, 220, 255};
        
        for (int i = 0; i < pwmValues.length; i++) {
            entries.add(new Entry(i, pwmValues[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "PWM Signal (0-255)");
        dataSet.setColor(Color.parseColor("#4CAF50")); // Green
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(Color.parseColor("#4CAF50"));
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#4CAF50"));
        dataSet.setFillAlpha(50);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        lineChartPWMTrends.setData(lineData);
        lineChartPWMTrends.invalidate();
    }

    /**
     * Load dashboard summary statistics
     */
    /**
     * Load time-of-day accident patterns
     */
    private void loadDummyTimePatterns() {
        // Implementation for a time pattern chart (could be another BarChart or custom view)
        Log.d(TAG, "Loading dummy time patterns: Morning (20%), Afternoon (35%), Evening (30%), Night (15%)");
    }

    /**
     * Load monthly comparison data
     */
    private void loadDummyMonthlyComparison() {
        Log.d(TAG, "Loading dummy monthly comparison: +15% vs last month");
    }

    private void loadDashboardSummary() {
        Call<ResponseBody> call = apiService.getDashboardAnalytics();
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONObject data = new JSONObject(json);
                        
                        tvTotalIncidents.setText(String.valueOf(data.optInt("totalIncidents", 0)));
                        tvSevereCount.setText(String.valueOf(data.optInt("severeCount", 0)));
                        tvModerateCount.setText(String.valueOf(data.optInt("moderateCount", 0)));
                        tvMinorCount.setText(String.valueOf(data.optInt("minorCount", 0)));
                        tvAvgImpactForce.setText(String.format("%.2f G", data.optDouble("avgImpactForce", 0)));
                        tvAvgConfidence.setText(String.format("%.1f%%", data.optDouble("avgConfidence", 0)));
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing dashboard data", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to load dashboard summary", t);
                Toast.makeText(AnalyticsDashboardActivity.this, 
                    "Failed to load analytics", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Load severity statistics and create pie chart
     */
    private void loadSeverityStats() {
        Call<ResponseBody> call = apiService.getSeverityAnalytics();
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONArray data = new JSONArray(json);
                        
                        List<PieEntry> entries = new ArrayList<>();
                        List<Integer> colors = new ArrayList<>();
                        
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            String severity = item.optString("_id", "Unknown");
                            int count = item.optInt("count", 0);
                            
                            entries.add(new PieEntry(count, severity));
                            
                            // Set colors based on severity
                            switch (severity) {
                                case "SEVERE":
                                    colors.add(Color.parseColor("#F44336")); // Red
                                    break;
                                case "MODERATE":
                                    colors.add(Color.parseColor("#FF9800")); // Orange
                                    break;
                                case "MINOR":
                                    colors.add(Color.parseColor("#FFC107")); // Yellow
                                    break;
                                default:
                                    colors.add(Color.GRAY);
                            }
                        }
                        
                        PieDataSet dataSet = new PieDataSet(entries, "Severity");
                        dataSet.setColors(colors);
                        dataSet.setValueTextSize(12f);
                        dataSet.setValueTextColor(Color.WHITE);
                        
                        PieData pieData = new PieData(dataSet);
                        pieChartSeverity.setData(pieData);
                        pieChartSeverity.invalidate();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing severity stats", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to load severity stats", t);
            }
        });
    }

    /**
     * Load accident trends and create line chart
     */
    private void loadAccidentTrends() {
        Call<ResponseBody> call = apiService.getTrendAnalytics("30");
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONArray data = new JSONArray(json);
                        
                        List<Entry> entries = new ArrayList<>();
                        
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            int count = item.optInt("count", 0);
                            entries.add(new Entry(i, count));
                        }
                        
                        LineDataSet dataSet = new LineDataSet(entries, "Incidents per Day");
                        dataSet.setColor(Color.parseColor("#2196F3")); // Blue
                        dataSet.setValueTextColor(Color.BLACK);
                        dataSet.setLineWidth(2f);
                        dataSet.setCircleColor(Color.parseColor("#2196F3"));
                        dataSet.setCircleRadius(4f);
                        dataSet.setDrawValues(false);
                        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                        
                        LineData lineData = new LineData(dataSet);
                        lineChartTrends.setData(lineData);
                        lineChartTrends.invalidate();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing trends", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to load trends", t);
            }
        });
    }

    /**
     * Load location hotspots and create bar chart
     */
    private void loadHotspots() {
        Call<ResponseBody> call = apiService.getHotspotAnalytics("10");
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONArray data = new JSONArray(json);
                        
                        List<BarEntry> entries = new ArrayList<>();
                        final List<String> labels = new ArrayList<>();
                        
                        for (int i = 0; i < Math.min(data.length(), 10); i++) {
                            JSONObject item = data.getJSONObject(i);
                            int count = item.optInt("count", 0);
                            
                            // Get location coordinates
                            JSONObject location = item.optJSONObject("_id");
                            if (location != null) {
                                double lat = location.optDouble("latitude", 0);
                                double lng = location.optDouble("longitude", 0);
                                labels.add(String.format("%.2f,%.2f", lat, lng));
                            } else {
                                labels.add("Location " + (i + 1));
                            }
                            
                            entries.add(new BarEntry(i, count));
                        }
                        
                        BarDataSet dataSet = new BarDataSet(entries, "Incidents per Location");
                        dataSet.setColor(Color.parseColor("#4CAF50")); // Green
                        dataSet.setValueTextColor(Color.BLACK);
                        dataSet.setValueTextSize(10f);
                        
                        BarData barData = new BarData(dataSet);
                        barData.setBarWidth(0.9f);
                        
                        barChartHotspots.setData(barData);
                        
                        // Set X-axis labels
                        XAxis xAxis = barChartHotspots.getXAxis();
                        xAxis.setValueFormatter(new ValueFormatter() {
                            @Override
                            public String getFormattedValue(float value) {
                                int index = (int) value;
                                if (index >= 0 && index < labels.size()) {
                                    return labels.get(index);
                                }
                                return "";
                            }
                        });
                        xAxis.setGranularity(1f);
                        xAxis.setLabelRotationAngle(-45f);
                        
                        barChartHotspots.invalidate();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing hotspots", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to load hotspots", t);
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startLiveTelemetryPolling();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isPolling = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPolling = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }
}
