package com.tipil.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.tipil.app.data.local.BookDao
import com.tipil.app.data.local.NotFoundScanDao
import com.tipil.app.data.local.TipilDatabase
import com.tipil.app.data.remote.GoogleBooksApi
import com.tipil.app.data.remote.K10plusApi
import com.tipil.app.data.remote.MusicBrainzApi
import com.tipil.app.data.remote.OpenLibraryApi
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

/**
 * Spaces requests out to at most one per [minIntervalMs].
 *
 * MusicBrainz caps callers at roughly one request a second and answers 503
 * once that is exceeded. Without this the app could fire several searches
 * back to back — a single lookup issues two, and retrying a list of items
 * issues many — and every throttled response would surface indistinguishably
 * from "no such release".
 *
 * Blocking is safe here: OkHttp runs interceptors on its own dispatcher
 * threads, never the main thread.
 */
class MinIntervalInterceptor(private val minIntervalMs: Long) : Interceptor {
    private val lock = Any()
    private var lastRequestAt = 0L

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        synchronized(lock) {
            val waitFor = lastRequestAt + minIntervalMs - System.currentTimeMillis()
            if (waitFor > 0) {
                try {
                    Thread.sleep(waitFor)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Interrupted while rate limiting")
                }
            }
            lastRequestAt = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicBrainzClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class K10plusClient

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
            .addMigrations(TipilDatabase.MIGRATION_1_2, TipilDatabase.MIGRATION_2_3, TipilDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(database: TipilDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    @Singleton
    fun provideNotFoundScanDao(database: TipilDatabase): NotFoundScanDao {
        return database.notFoundScanDao()
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
            // MusicBrainz allows ~1 request/second; exceeding it returns 503.
            .addInterceptor(MinIntervalInterceptor(minIntervalMs = 1100))

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
    fun provideOpenLibraryApi(client: OkHttpClient): OpenLibraryApi {
        return Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenLibraryApi::class.java)
    }

    /**
     * K10plus is a public library service; it asks callers to identify
     * themselves, same as MusicBrainz.
     */
    @Provides
    @Singleton
    @K10plusClient
    fun provideK10plusClient(): OkHttpClient {
        val userAgentInterceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Tipil/${BuildConfig.VERSION_NAME} (tipil.app)")
                    .build()
            )
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
    fun provideK10plusApi(@K10plusClient client: OkHttpClient): K10plusApi {
        return Retrofit.Builder()
            .baseUrl("https://sru.k10plus.de/")
            .client(client)
            // MARCXML is read as a raw ResponseBody; Gson is only here so the
            // builder has a converter for any future JSON endpoints.
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(K10plusApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
