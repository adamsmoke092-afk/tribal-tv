package com.example.iptvapp

import android.app.Application
import com.example.iptvapp.supabase.SupabaseClient

/**
 * Application class for Tribal TV.
 * Initializes Supabase client on startup.
 */
class TribalTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.initialize(this)
    }
}