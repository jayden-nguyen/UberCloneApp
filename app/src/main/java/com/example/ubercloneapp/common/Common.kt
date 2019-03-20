package com.example.ubercloneapp.common

import com.example.ubercloneapp.remote.IGoogleApi
import com.example.ubercloneapp.remote.RetrofitClient

class Common {
    companion object {
        val baseUrl = "https://maps.googleapis.com"
        fun getGoogleAPI(): IGoogleApi? {
            return RetrofitClient.getClient(baseUrl)?.create(IGoogleApi::class.java)
        }
    }
}