package com.example.ubercloneapp.remote

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

interface IGoogleApi {
    @GET
    fun getPath(@Url url: String): Call<String>

}