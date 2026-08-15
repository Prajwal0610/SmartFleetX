package com.example.smartfleetx.network;

import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    // Authentication
    @POST("auth/login")
    Call<JsonObject> login(@Body JsonObject body);

    @POST("auth/register")
    Call<JsonObject> register(@Body JsonObject body);

    @PUT("auth/update")
    Call<JsonObject> updateProfile(@Body JsonObject body);

    @PUT("auth/change-password")
    Call<JsonObject> changePassword(@Body JsonObject body);

    // Incidents
    @POST("incidents")
    Call<ResponseBody> createIncident(@Body JsonObject body);

    @GET("incidents/{id}")
    Call<ResponseBody> getIncident(@Path("id") String incidentId);

    @GET("incidents")
    Call<ResponseBody> getIncidents();

    // Analytics
    @GET("analytics/dashboard")
    Call<ResponseBody> getDashboardAnalytics();

    @GET("analytics/trends")
    Call<ResponseBody> getTrendAnalytics(@Query("days") String days);

    @GET("analytics/severity")
    Call<ResponseBody> getSeverityAnalytics();

    @GET("analytics/hotspots")
    Call<ResponseBody> getHotspotAnalytics(@Query("limit") String limit);

    @GET("analytics/patterns")
    Call<ResponseBody> getTimePatterns();

    @GET("analytics/monthly")
    Call<ResponseBody> getMonthlyComparison();

    // Sync
    @POST("incidents/sync")
    Call<ResponseBody> syncIncidents(@Body JsonObject body);

    @GET("incidents/pending")
    Call<ResponseBody> getPendingIncidents();

    // Access Control
    @POST("access/grant")
    Call<ResponseBody> grantAccess(@Body JsonObject body);

    @GET("access/incident/{token}")
    Call<ResponseBody> getIncidentWithToken(@Path("token") String token);
    
    @GET("access/tokens/{incidentId}")
    Call<ResponseBody> getIncidentAccessTokens(@Path("incidentId") String incidentId);
    
    @POST("access/revoke/{tokenId}")
    Call<ResponseBody> revokeAccess(@Path("tokenId") String tokenId, @Body JsonObject body);

    // Health Monitoring (Backend)
    @GET("health/status")
    Call<ResponseBody> getSystemHealth();

    @POST("health/heartbeat")
    Call<ResponseBody> sendHeartbeat(@Body JsonObject body);

    // Relay Control
    @GET("relay/1")
    Call<JsonObject> toggleRelay1(@Query("state") int state);

    @GET("relay/2")
    Call<JsonObject> toggleRelay2(@Query("state") int state);

    @GET("relay/3")
    Call<JsonObject> toggleRelay3(@Query("state") int state);

    @GET("relay/4")
    Call<JsonObject> toggleRelay4(@Query("state") int state);

    @GET("relay/status")
    Call<JsonObject> getRelayStatus();

    // Hardware Telemetry
    @GET("hardware/latest")
    Call<JsonObject> getLatestHardwareData();

    // Update smartphone location
    @POST("hardware/phone-location")
    Call<JsonObject> updatePhoneLocation(@Body JsonObject body);
}
