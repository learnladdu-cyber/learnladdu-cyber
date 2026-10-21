package com.ashis.notesphare.auth

import com.ashis.notesphare.BuildConfig

object SupabaseConfigProvider {
    fun fromBuild(): SupabaseConfig = SupabaseConfig(
        url = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        redirectUrl = BuildConfig.SUPABASE_REDIRECT_URL,
        storageBucket = BuildConfig.SUPABASE_STORAGE_BUCKET
    )
}
