package com.example.hexkeyboard.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.engine.PredictionEngine
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.logic.managers.VoiceRecognitionHelper
import com.example.hexkeyboard.service.SpellCheckerManager
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
