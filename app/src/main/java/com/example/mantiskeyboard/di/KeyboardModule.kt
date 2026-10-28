package com.example.mantiskeyboard.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.mantiskeyboard.data.repository.EmojiProvider
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.logic.engine.PredictionEngine
import com.example.mantiskeyboard.logic.managers.FeedbackManager
import com.example.mantiskeyboard.logic.managers.VoiceRecognitionHelper
import com.example.mantiskeyboard.service.SpellCheckerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KeyboardModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return ThemeUtils.getDataStore(context)
    }

    @Provides
    @Singleton
    fun providePredictionEngine(@ApplicationContext context: Context): PredictionEngine {
        return PredictionEngine(context)
    }

    @Provides
    @Singleton
    fun provideFeedbackManager(): FeedbackManager {
        return FeedbackManager
    }

    @Provides
    @Singleton
    fun provideEmojiProvider(): EmojiProvider {
        return EmojiProvider
    }

    @Provides
    @Singleton
    fun provideVoiceRecognitionHelper(@ApplicationContext context: Context): VoiceRecognitionHelper {
        return VoiceRecognitionHelper(context)
    }

    @Provides
    @Singleton
    fun provideSpellCheckerManager(predictionEngine: PredictionEngine): SpellCheckerManager {
        return SpellCheckerManager(predictionEngine)
    }
}
