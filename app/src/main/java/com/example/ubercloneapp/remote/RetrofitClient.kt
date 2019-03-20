package com.example.ubercloneapp.remote

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class RetrofitClient {
    companion object {
        var retrofit: Retrofit? = null

        fun getClient(baseUrl: String): Retrofit? {
           if (retrofit == null) {
               retrofit = Retrofit.Builder()
                   .baseUrl(baseUrl)
                   .addConverterFactory(ScalarsConverterFactory.create())
                   .build()
           }

            return retrofit
        }
    }
}