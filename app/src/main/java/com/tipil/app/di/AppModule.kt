package com.tipil.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.tipil.app.data.local.BookDao
import com.tipil.app.data.local.TipilDatabase
import com.tipil.app.data.remote.GoogleBooksApi
import com.tipil.app.data.remote.MusicBrainzApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.tipil.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicBrainzClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TipilDatabase {
        return Room.databaseBuilder(
            context,
            TipilDatabase::class.java,
            "tipil_database"
        )
            .addMigrations(TipilDatabase.MIGRATION_1_2, TipilDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(database: TipilDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGoogleBooksApi(client: OkHttpClient): GoogleBooksApi {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/books/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleBooksApi::class.java)
    }

    @Provides
    @Singleton
    @MusicBrainzClient
    fun provideMusicBrainzClient(): OkHttpClient {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Tipil/${BuildConfig.VERSION_NAME} (tipil.app)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApi(@MusicBrainzClient client: OkHttpClient): MusicBrainzApi {
        return Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/ws/2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicBrainzApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
