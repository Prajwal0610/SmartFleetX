package com.example.smartfleetx.activity; // change to your package

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.smartfleetx.R;

public class SplashActivity extends AppCompatActivity {

    private static final long FALLBACK_MS = 3000L; // fallback timeout
    private final Handler handler = new Handler();
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final LottieAnimationView lottie = findViewById(R.id.lottieSplash);
        View skip = findViewById(R.id.tvSkip);

        // Optional: show skip if you want
        if (skip != null) {
            skip.setVisibility(View.GONE); // set VISIBLE to enable skipping
            skip.setOnClickListener(v -> navigateNext());
        }

        // animation end listener
        lottie.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                navigateNext();
            }
        });

        // fallback: in case animation fails to end/callback doesn't fire
        handler.postDelayed(this::navigateNext, FALLBACK_MS);
    }

    private synchronized void navigateNext() {
        if (navigated) return;
        navigated = true;
        // change LoginActivity.class to MainActivity.class if you'd rather
        startActivity(new Intent(SplashActivity.this,LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
