package com.example.paldoapp // Your package name

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import kotlin.jvm.java

class SplashActivity : AppCompatActivity() {

    // Duration of the splash screen in milliseconds
    private val SPLASH_DELAY_MS: Long = 2000 // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout for your splash screen.
        // This layout can be very simple, e.g., just an ImageView with your logo.
        setContentView(R.layout.activity_splash) // Make sure you have activity_try.xml

        // Use a Handler to delay the transition to the main activity.
        Handler(Looper.getMainLooper()).postDelayed({
            // Create an Intent to start your main activity (e.g., MainActivity).
            val mainIntent = Intent(this@SplashActivity, MainActivity::class.java) // Replace MainActivity with your actual main activity
            startActivity(mainIntent)

            // Finish TryActivity so the user can't navigate back to it.
            finish()
        }, SPLASH_DELAY_MS)
    }
}