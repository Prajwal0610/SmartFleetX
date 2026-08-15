package com.example.smartfleetx.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.example.smartfleetx.model.DriverState;
import com.example.smartfleetx.model.IncidentData;
import com.example.smartfleetx.model.SensorLog;
import com.example.smartfleetx.model.VehicleData;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;

import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ReportGenerator - Generates professional PDF accident reports
 * Features:
 * - Automatic accident summary
 * - Graphical sensor data visualization
 * - GPS path and impact plotting
 * - Legal/insurance-ready formatting
 * - Multi-media embedding
 */
public class ReportGenerator {

    private static final String TAG = "ReportGenerator";

    private Context context;
    private Geocoder geocoder;

    // PDF styling
    private static final DeviceRgb COLOR_HEADER = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb COLOR_SEVERE = new DeviceRgb(231, 76, 60);
    private static final DeviceRgb COLOR_MODERATE = new DeviceRgb(230, 126, 34);
    private static final DeviceRgb COLOR_MINOR = new DeviceRgb(241, 196, 15);

    public ReportGenerator(Context context) {
        this.context = context;
        this.geocoder = new Geocoder(context, Locale.getDefault());
    }

    /**
     * Generate comprehensive accident report PDF
     *
     * @param incidentData   Incident data
     * @param sensorLogs     Sensor logs (pre/post crash)
     * @param outputDir      Output directory
     * @param templateType   STANDARD | INSURANCE | LEGAL
     * @return Path to generated PDF file
     */
    public String generateReport(IncidentData incidentData, List<SensorLog> sensorLogs,
                                 File outputDir, String templateType) {
        return generateReport(incidentData, sensorLogs, outputDir, templateType, null, null);
    }

    /**
     * Generate comprehensive accident report PDF with optional recording timestamps.
     *
     * @param incidentData        Incident data
     * @param sensorLogs          Sensor logs (pre/post crash)
     * @param outputDir           Output directory
     * @param templateType        STANDARD | INSURANCE | LEGAL
     * @param recordingStartTime  Recording start epoch ms (or null)
     * @param recordingEndTime    Recording end epoch ms (or null)
     * @return Path to generated PDF file
     */
    public String generateReport(IncidentData incidentData, List<SensorLog> sensorLogs,
                                 File outputDir, String templateType,
                                 Long recordingStartTime, Long recordingEndTime) {
        try {
            // Create output file
            String fileName = String.format("Accident_Report_%s_%d.pdf",
                incidentData.getId(), System.currentTimeMillis());
            File outputFile = new File(outputDir, fileName);

            // Create PDF document
            PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Attach page-number + timestamp footer handler
            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new TimestampFooterHandler(PdfFontFactory.createFont()));

            // Set fonts
            PdfFont boldFont = PdfFontFactory.createFont();
            PdfFont regularFont = PdfFontFactory.createFont();

            // Add content based on template type
            switch (templateType) {
                case "INSURANCE":
                    generateInsuranceReport(document, incidentData, sensorLogs,
                            boldFont, regularFont, recordingStartTime, recordingEndTime);
                    break;
                case "LEGAL":
                    generateLegalReport(document, incidentData, sensorLogs,
                            boldFont, regularFont, recordingStartTime, recordingEndTime);
                    break;
                default: // STANDARD
                    generateStandardReport(document, incidentData, sensorLogs,
                            boldFont, regularFont, recordingStartTime, recordingEndTime);
                    break;
            }

            // Close document
            document.close();

            Log.i(TAG, "Report generated successfully: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error generating report", e);
            return null;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PAGE FOOTER – timestamp + page number
    // ────────────────────────────────────────────────────────────────────────

    private static class TimestampFooterHandler implements IEventHandler {
        private final PdfFont font;
        private final String generated;

        TimestampFooterHandler(PdfFont font) {
            this.font = font;
            this.generated = new SimpleDateFormat("dd MMM yyyy  HH:mm:ss z", Locale.getDefault())
                    .format(new Date());
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            com.itextpdf.kernel.pdf.PdfPage page = docEvent.getPage();
            PdfDocument pdf = docEvent.getDocument();
            int pageNum = pdf.getPageNumber(page);
            Rectangle pageSize = page.getPageSize();

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
            try (com.itextpdf.layout.Canvas canvas = new com.itextpdf.layout.Canvas(pdfCanvas, pageSize)) {
                canvas.setFont(font).setFontSize(7)
                        .showTextAligned(
                                "Generated: " + generated + "   |   Page " + pageNum,
                                pageSize.getWidth() / 2,
                                20, com.itextpdf.layout.properties.TextAlignment.CENTER);
            } catch (Exception ignored) {}
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // STANDARD REPORT
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generate standard accident report (with optional recording timestamps)
     */
    private void generateStandardReport(Document document, IncidentData incidentData,
                                        List<SensorLog> sensorLogs, PdfFont boldFont,
                                        PdfFont regularFont,
                                        Long recordingStartTime, Long recordingEndTime) throws Exception {
        
        // Title
        Paragraph title = new Paragraph("ACCIDENT INCIDENT REPORT")
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(COLOR_HEADER)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(title);

        // Incident ID and timestamps
        SimpleDateFormat sdf    = new SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm:ss z", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss  dd MMM yyyy", Locale.getDefault());
        StringBuilder metaText = new StringBuilder();
        metaText.append("Incident ID: ").append(incidentData.getId()).append("\n");
        metaText.append("Report Generated: ").append(sdf.format(new Date()));
        if (recordingStartTime != null) {
            metaText.append("\nRecording Started: ").append(timeFmt.format(new Date(recordingStartTime)));
        }
        if (recordingEndTime != null) {
            metaText.append("   |   Stopped: ").append(timeFmt.format(new Date(recordingEndTime)));
        }
        Paragraph meta = new Paragraph(metaText.toString())
            .setFont(regularFont)
            .setFontSize(10)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(30);
        document.add(meta);

        // Severity badge
        addSeverityBadge(document, incidentData.getSeverity(), boldFont);

        // Incident Summary
        document.add(new Paragraph("INCIDENT SUMMARY")
            .setFont(boldFont)
            .setFontSize(16)
            .setMarginTop(20)
            .setMarginBottom(10));
        
        Table summaryTable = new Table(2);
        summaryTable.setWidth(500);

        SimpleDateFormat dtFmt = new SimpleDateFormat("dd MMM yyyy  HH:mm:ss z", Locale.getDefault());
        addTableRow(summaryTable, "Incident Date & Time:",
            dtFmt.format(new Date(incidentData.getTimestamp())), boldFont, regularFont);
        addTableRow(summaryTable, "Severity:", incidentData.getSeverity(), boldFont, regularFont);
        addTableRow(summaryTable, "Confidence Score:",
            incidentData.getConfidenceScore() + "%", boldFont, regularFont);
        addTableRow(summaryTable, "Impact Force:",
            String.format("%.2f G", incidentData.getImpactForce()), boldFont, regularFont);

        // Recording window
        if (recordingStartTime != null) {
            addTableRow(summaryTable, "Recording Start:",
                dtFmt.format(new Date(recordingStartTime)), boldFont, regularFont);
        }
        if (recordingEndTime != null) {
            addTableRow(summaryTable, "Recording End:",
                dtFmt.format(new Date(recordingEndTime)), boldFont, regularFont);
            if (recordingStartTime != null) {
                long dur = (recordingEndTime - recordingStartTime) / 1000;
                addTableRow(summaryTable, "Recording Duration:",
                    String.format("%02d:%02d min", dur / 60, dur % 60), boldFont, regularFont);
            }
        }

        // Location
        String locationStr = String.format("%.6f, %.6f",
            incidentData.getLatitude(), incidentData.getLongitude());
        if (incidentData.getAddress() != null) {
            locationStr += "\n" + incidentData.getAddress();
        }
        addTableRow(summaryTable, "Location:", locationStr, boldFont, regularFont);

        document.add(summaryTable);

        // Vehicle Data
        if (incidentData.getVehicleData() != null) {
            document.add(new Paragraph("VEHICLE DATA AT TIME OF INCIDENT")
                .setFont(boldFont)
                .setFontSize(16)
                .setMarginTop(20)
                .setMarginBottom(10));
            
            VehicleData vd = incidentData.getVehicleData();
            Table vehicleTable = new Table(2);
            vehicleTable.setWidth(500);
            
            addTableRow(vehicleTable, "Speed:", vd.getSpeed() + " km/h", boldFont, regularFont);
            addTableRow(vehicleTable, "RPM:", String.valueOf(vd.getRpm()), boldFont, regularFont);
            addTableRow(vehicleTable, "Engine Load:", vd.getEngineLoad() + "%", boldFont, regularFont);
            addTableRow(vehicleTable, "Hard Brake:", vd.isHardBrake() ? "YES" : "NO", boldFont, regularFont);
            
            document.add(vehicleTable);
        }

        // Driver State
        if (incidentData.getDriverState() != null) {
            document.add(new Paragraph("DRIVER STATE AT TIME OF INCIDENT")
                .setFont(boldFont)
                .setFontSize(16)
                .setMarginTop(20)
                .setMarginBottom(10));
            
            DriverState ds = incidentData.getDriverState();
            Table driverTable = new Table(2);
            driverTable.setWidth(500);
            
            addTableRow(driverTable, "Drowsiness Detected:", ds.isDrowsy() ? "YES" : "NO", boldFont, regularFont);
            addTableRow(driverTable, "Distraction Detected:", ds.isDistracted() ? "YES" : "NO", boldFont, regularFont);
            addTableRow(driverTable, "Attention Score:", ds.getAttentionScore() + "/100", boldFont, regularFont);
            addTableRow(driverTable, "Head Pose:", ds.getHeadPose(), boldFont, regularFont);
            
            document.add(driverTable);
        }

        // Sensor Data Summary
        if (sensorLogs != null && !sensorLogs.isEmpty()) {
            document.add(new Paragraph("SENSOR DATA ANALYSIS")
                .setFont(boldFont)
                .setFontSize(16)
                .setMarginTop(20)
                .setMarginBottom(10));

            // Find peak values
            float peakGForce = 0;
            float maxSpeed = 0;
            long firstTs = Long.MAX_VALUE, lastTs = Long.MIN_VALUE;
            for (SensorLog log : sensorLogs) {
                peakGForce = Math.max(peakGForce, log.gForceMagnitude);
                maxSpeed   = Math.max(maxSpeed,   log.speed);
                if (log.timestamp > 0) {
                    firstTs = Math.min(firstTs, log.timestamp);
                    lastTs  = Math.max(lastTs,  log.timestamp);
                }
            }

            Table sensorTable = new Table(2);
            sensorTable.setWidth(500);

            SimpleDateFormat tsFmt = new SimpleDateFormat("HH:mm:ss.SSS  dd-MMM-yyyy", Locale.getDefault());
            if (firstTs != Long.MAX_VALUE) {
                addTableRow(sensorTable, "First Sample Timestamp:",
                    tsFmt.format(new Date(firstTs)), boldFont, regularFont);
            }
            if (lastTs != Long.MIN_VALUE) {
                addTableRow(sensorTable, "Last Sample Timestamp:",
                    tsFmt.format(new Date(lastTs)), boldFont, regularFont);
            }
            addTableRow(sensorTable, "Peak G-Force:",   String.format("%.2f G",    peakGForce), boldFont, regularFont);
            addTableRow(sensorTable, "Max Speed:",       String.format("%.1f km/h", maxSpeed),   boldFont, regularFont);
            addTableRow(sensorTable, "Total Samples:",   String.valueOf(sensorLogs.size()),      boldFont, regularFont);

            document.add(sensorTable);

            // ── Timestamped per-sample log (up to 30 rows) ───────────────
            document.add(new Paragraph("SENSOR LOG — Per Sample Timestamps")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(16)
                .setMarginBottom(6));

            float[] colWidths = {3, 2, 2, 2, 2};
            Table logTable = new Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(colWidths));
            logTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
            logTable.addHeaderCell(new Cell().add(new Paragraph("Timestamp").setFont(boldFont).setFontSize(9)));
            logTable.addHeaderCell(new Cell().add(new Paragraph("G-Force (G)").setFont(boldFont).setFontSize(9)));
            logTable.addHeaderCell(new Cell().add(new Paragraph("Speed (km/h)").setFont(boldFont).setFontSize(9)));
            logTable.addHeaderCell(new Cell().add(new Paragraph("Lat").setFont(boldFont).setFontSize(9)));
            logTable.addHeaderCell(new Cell().add(new Paragraph("Lon").setFont(boldFont).setFontSize(9)));

            SimpleDateFormat rowFmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            int rowCount = 0;
            for (SensorLog log : sensorLogs) {
                String ts = log.timestamp > 0 ? rowFmt.format(new Date(log.timestamp)) : "N/A";
                logTable.addCell(new Cell().add(new Paragraph(ts).setFont(regularFont).setFontSize(8)));
                logTable.addCell(new Cell().add(new Paragraph(String.format("%.3f", log.gForceMagnitude)).setFont(regularFont).setFontSize(8)));
                logTable.addCell(new Cell().add(new Paragraph(String.format("%.1f", log.speed)).setFont(regularFont).setFontSize(8)));
                logTable.addCell(new Cell().add(new Paragraph(String.format("%.5f", log.latitude)).setFont(regularFont).setFontSize(8)));
                logTable.addCell(new Cell().add(new Paragraph(String.format("%.5f", log.longitude)).setFont(regularFont).setFontSize(8)));
                if (++rowCount >= 30) break;
            }
            document.add(logTable);

            // Add Sensor Chart
            Bitmap chartBitmap = createSensorChart(sensorLogs, 500, 200);
            if (chartBitmap != null) {
                document.add(new Paragraph("G-Force Trend Chart")
                    .setFont(boldFont)
                    .setFontSize(12)
                    .setMarginTop(10));
                
                try {
                    java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                    chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    Image chartImage = new Image(ImageDataFactory.create(stream.toByteArray()));
                    chartImage.setWidth(500);
                    document.add(chartImage);
                } catch (Exception e) {
                    Log.e(TAG, "Error adding chart to PDF", e);
                }
            }

            // Add GPS Path Plot
            Bitmap gpsBitmap = createGpsPathPlot(sensorLogs, 500, 200);
            if (gpsBitmap != null) {
                document.add(new Paragraph("GPS Impact Path")
                    .setFont(boldFont)
                    .setFontSize(12)
                    .setMarginTop(20));
                
                try {
                    java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                    gpsBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    Image gpsImage = new Image(ImageDataFactory.create(stream.toByteArray()));
                    gpsImage.setWidth(500);
                    document.add(gpsImage);
                } catch (Exception e) {
                    Log.e(TAG, "Error adding GPS plot to PDF", e);
                }
            }
        }

        // Footer
        document.add(new Paragraph("\n\nThis report is automatically generated by Smart Fleet X system and contains data captured at the time of the incident.")
            .setFont(regularFont)
            .setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(30));
    }

    // ────────────────────────────────────────────────────────────────────────
    // INSURANCE REPORT
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generate insurance-specific report (with optional recording timestamps)
     */
    private void generateInsuranceReport(Document document, IncidentData incidentData,
                                         List<SensorLog> sensorLogs, PdfFont boldFont,
                                         PdfFont regularFont,
                                         Long recordingStartTime, Long recordingEndTime) throws Exception {
        // Base standard content first
        generateStandardReport(document, incidentData, sensorLogs, boldFont, regularFont,
                recordingStartTime, recordingEndTime);
        
        // Add insurance-specific sections
        document.add(new Paragraph("INSURANCE INFORMATION")
            .setFont(boldFont)
            .setFontSize(16)
            .setMarginTop(20)
            .setMarginBottom(10));
        
        Table insuranceTable = new Table(2);
        insuranceTable.setWidth(500);
        
        addTableRow(insuranceTable, "Claim Filed:", 
            incidentData.isInsuranceClaimFiled() ? "YES" : "NO", boldFont, regularFont);
        if (incidentData.getClaimNumber() != null) {
            addTableRow(insuranceTable, "Claim Number:", 
                incidentData.getClaimNumber(), boldFont, regularFont);
        }
        
        document.add(insuranceTable);
    }

    // ────────────────────────────────────────────────────────────────────────
    // LEGAL REPORT
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generate legal-evidence report (with optional recording timestamps)
     */
    private void generateLegalReport(Document document, IncidentData incidentData,
                                     List<SensorLog> sensorLogs, PdfFont boldFont,
                                     PdfFont regularFont,
                                     Long recordingStartTime, Long recordingEndTime) throws Exception {
        generateStandardReport(document, incidentData, sensorLogs, boldFont, regularFont,
                recordingStartTime, recordingEndTime);
        
        // Add evidence integrity section
        document.add(new Paragraph("EVIDENCE INTEGRITY")
            .setFont(boldFont)
            .setFontSize(16)
            .setMarginTop(20)
            .setMarginBottom(10));
        
        Table integrityTable = new Table(2);
        integrityTable.setWidth(500);
        
        addTableRow(integrityTable, "Data Hash:", 
            incidentData.getDataHash() != null ? incidentData.getDataHash().substring(0, 16) + "..." : "N/A", 
            boldFont, regularFont);
        addTableRow(integrityTable, "Integrity Score:", 
            String.format("%.2f%%", incidentData.getIntegrityScore() * 100), 
            boldFont, regularFont);
        addTableRow(integrityTable, "Status:", 
            incidentData.getIntegrityStatus(), boldFont, regularFont);
        
        document.add(integrityTable);
    }

    /**
     * Add severity badge to document
     */
    private void addSeverityBadge(Document document, String severity, PdfFont boldFont) {
        DeviceRgb color;
        switch (severity) {
            case "SEVERE":
                color = COLOR_SEVERE;
                break;
            case "MODERATE":
                color = COLOR_MODERATE;
                break;
            default:
                color = COLOR_MINOR;
                break;
        }

        Paragraph badge = new Paragraph(severity + " ACCIDENT")
            .setFont(boldFont)
            .setFontSize(18)
            .setFontColor(DeviceRgb.WHITE)
            .setBackgroundColor(color)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(10);
        
        document.add(badge);
    }

    /**
     * Helper method to add table row
     */
    private void addTableRow(Table table, String label, String value, 
                            PdfFont boldFont, PdfFont regularFont) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(boldFont))
            .setBorder(null).setPadding(5));
        table.addCell(new Cell().add(new Paragraph(value).setFont(regularFont))
            .setBorder(null).setPadding(5));
    }

    /**
     * Create simple sensor data chart (as image)
     */
    private Bitmap createSensorChart(List<SensorLog> sensorLogs, int width, int height) {
        if (sensorLogs == null || sensorLogs.isEmpty()) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw white background
        canvas.drawColor(Color.WHITE);
        
        Paint linePaint = new Paint();
        linePaint.setColor(Color.BLUE);
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);
        
        // Find max G-force for scaling
        float maxG = 0;
        for (SensorLog log : sensorLogs) {
            maxG = Math.max(maxG, log.gForceMagnitude);
        }
        
        // Plot data points
        float xStep = (float) width / sensorLogs.size();
        for (int i = 1; i < sensorLogs.size(); i++) {
            float x1 = (i - 1) * xStep;
            float y1 = height - (sensorLogs.get(i - 1).gForceMagnitude / maxG * height * 0.9f);
            float x2 = i * xStep;
            float y2 = height - (sensorLogs.get(i).gForceMagnitude / maxG * height * 0.9f);
            
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }
        
        return bitmap;
    }

    /**
     * Create simple GPS path plot (as image)
     */
    private Bitmap createGpsPathPlot(List<SensorLog> sensorLogs, int width, int height) {
        if (sensorLogs == null || sensorLogs.isEmpty()) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.RED);
        pathPaint.setStrokeWidth(3);
        pathPaint.setAntiAlias(true);
        pathPaint.setStyle(Paint.Style.STROKE);

        // Find bounding box
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (SensorLog log : sensorLogs) {
            if (log.latitude == 0 && log.longitude == 0) continue;
            minLat = Math.min(minLat, log.latitude);
            maxLat = Math.max(maxLat, log.latitude);
            minLon = Math.min(minLon, log.longitude);
            maxLon = Math.max(maxLon, log.longitude);
        }

        if (maxLat == minLat || maxLon == minLon) return null;

        float padding = 20;
        float plotWidth = width - 2 * padding;
        float plotHeight = height - 2 * padding;

        for (int i = 1; i < sensorLogs.size(); i++) {
            SensorLog p1 = sensorLogs.get(i - 1);
            SensorLog p2 = sensorLogs.get(i);
            if (p1.latitude == 0 || p2.latitude == 0) continue;

            float x1 = padding + (float)((p1.longitude - minLon) / (maxLon - minLon) * plotWidth);
            float y1 = height - (padding + (float)((p1.latitude - minLat) / (maxLat - minLat) * plotHeight));
            float x2 = padding + (float)((p2.longitude - minLon) / (maxLon - minLon) * plotWidth);
            float y2 = height - (padding + (float)((p2.latitude - minLat) / (maxLat - minLat) * plotHeight));

            canvas.drawLine(x1, y1, x2, y2, pathPaint);
        }

        return bitmap;
    }
    /**
     * Generate system health and reliability report
     */
    public String generateSystemHealthReport(android.database.Cursor cursor, File outputDir) {
        try {
            String fileName = "System_Health_Report_" + System.currentTimeMillis() + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            PdfFont boldFont = PdfFontFactory.createFont();
            PdfFont regularFont = PdfFontFactory.createFont();

            // Title
            Paragraph title = new Paragraph("SYSTEM RELIABILITY REPORT")
                .setFont(boldFont)
                .setFontSize(24)
                .setFontColor(COLOR_HEADER)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
            document.add(title);

            // Generated timestamp
            Paragraph meta = new Paragraph("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()))
                .setFont(regularFont)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30);
            document.add(meta);

            if (cursor != null && cursor.moveToFirst()) {
                // Summary Statistics
                int totalLogs = cursor.getCount();
                int healthyCount = 0;
                long totalUptime = 0;
                
                // Reset cursor to start
                cursor.moveToFirst();
                do {
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    long uptime = cursor.getLong(cursor.getColumnIndexOrThrow("uptime_ms"));
                    
                    if ("HEALTHY".equals(status)) healthyCount++;
                    totalUptime = Math.max(totalUptime, uptime); // Approx max uptime in period
                    
                } while (cursor.moveToNext());

                float healthyPercent = (float) healthyCount / totalLogs * 100;
                
                // Add Summary Section
                document.add(new Paragraph("RELIABILITY SUMMARY")
                    .setFont(boldFont)
                    .setFontSize(16)
                    .setMarginBottom(10));
                    
                Table summaryTable = new Table(2);
                summaryTable.setWidth(500);
                addTableRow(summaryTable, "Total Logic Records:", String.valueOf(totalLogs), boldFont, regularFont);
                addTableRow(summaryTable, "System Health Score:", String.format("%.1f%%", healthyPercent), boldFont, regularFont);
                
                long hours = totalUptime / (1000 * 60 * 60);
                addTableRow(summaryTable, "Max Continuous Uptime:", hours + " hours", boldFont, regularFont);
                document.add(summaryTable);

                // Logs Table
                document.add(new Paragraph("\nDETAILED LOGS (Last 50 Entries)")
                    .setFont(boldFont)
                    .setFontSize(16)
                    .setMarginTop(20)
                    .setMarginBottom(10));

                float[] columnWidths = {4, 3, 2, 2, 3}; // Relative widths
                Table logsTable = new Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(columnWidths));
                logsTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

                // Header
                logsTable.addHeaderCell(new Cell().add(new Paragraph("Time").setFont(boldFont).setFontSize(10)));
                logsTable.addHeaderCell(new Cell().add(new Paragraph("Status").setFont(boldFont).setFontSize(10)));
                logsTable.addHeaderCell(new Cell().add(new Paragraph("Score").setFont(boldFont).setFontSize(10)));
                logsTable.addHeaderCell(new Cell().add(new Paragraph("Battery").setFont(boldFont).setFontSize(10)));
                logsTable.addHeaderCell(new Cell().add(new Paragraph("Network").setFont(boldFont).setFontSize(10)));

                // Add rows (limit to 50 for brevity)
                cursor.moveToFirst();
                int count = 0;
                SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                
                do {
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    float score = cursor.getFloat(cursor.getColumnIndexOrThrow("overall_score"));
                    int battery = cursor.getInt(cursor.getColumnIndexOrThrow("battery_level"));
                    String network = cursor.getString(cursor.getColumnIndexOrThrow("network_status"));

                    logsTable.addCell(new Cell().add(new Paragraph(timeFormat.format(new Date(timestamp))).setFont(regularFont).setFontSize(9)));
                    logsTable.addCell(new Cell().add(new Paragraph(status).setFont(regularFont).setFontSize(9)));
                    logsTable.addCell(new Cell().add(new Paragraph(String.format("%.0f%%", score * 100)).setFont(regularFont).setFontSize(9)));
                    logsTable.addCell(new Cell().add(new Paragraph(battery + "%").setFont(regularFont).setFontSize(9)));
                    logsTable.addCell(new Cell().add(new Paragraph(network).setFont(regularFont).setFontSize(9)));

                    count++;
                } while (cursor.moveToNext() && count < 50);
                
                document.add(logsTable);
            } else {
                document.add(new Paragraph("No health logs available for this period."));
            }

            document.close();
            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error generating health report", e);
            return null;
        }
    }
}
