package com.euhomy.fridge

import android.app.Application

/**
 * Application subclass. Lightweight — no DI framework, just a hook for
 * future global initialisation if needed.
 */
class EuhomyApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
