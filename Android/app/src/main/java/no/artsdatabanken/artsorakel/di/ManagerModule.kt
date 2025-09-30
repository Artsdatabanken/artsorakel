package no.artsdatabanken.artsorakel.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.manager.DrawerManager
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.manager.NavigationManager
import no.artsdatabanken.artsorakel.manager.NavigationStackManager
import no.artsdatabanken.artsorakel.manager.PermissionManager
import no.artsdatabanken.artsorakel.manager.ThemeManager
import no.artsdatabanken.artsorakel.utils.SettingsManager
import javax.inject.Singleton

/**
 * Hilt module for providing manager dependencies.
 * Provides manager instances that handle various app responsibilities.
 */
@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    
    /**
     * Provides the NavigationStackManager as a singleton.
     * This manager handles the navigation stack for overlays.
     */
    @Provides
    @Singleton
    fun provideNavigationStackManager(): NavigationStackManager {
        return NavigationStackManager()
    }
    
    /**
     * Provides the NavigationManager as a singleton.
     * This manager handles fragment navigation and backstack management.
     * Note: Must call initialize() before use.
     */
    @Provides
    @Singleton
    fun provideNavigationManager(
        stackManager: NavigationStackManager,
        gson: com.google.gson.Gson
    ): NavigationManager {
        return NavigationManager(stackManager, gson)
    }
    
    /**
     * Provides the PermissionManager as a singleton.
     * This manager handles camera and storage permission requests.
     * Note: Must call initialize() before use.
     */
    @Provides
    @Singleton
    fun providePermissionManager(): PermissionManager {
        return PermissionManager()
    }
    
    /**
     * Provides the ThemeManager as a singleton.
     * This manager handles light/dark theme switching.
     */
    @Provides
    @Singleton
    fun provideThemeManager(
        @ApplicationContext context: Context
    ): ThemeManager {
        return ThemeManager(context)
    }
    
    /**
     * Provides the LanguageManager as a singleton.
     * This manager handles multi-language support and locale switching.
     */
    @Provides
    @Singleton
    fun provideLanguageManager(
        @ApplicationContext context: Context
    ): LanguageManager {
        return LanguageManager(context)
    }
    
    /**
     * Provides the SettingsManager as a singleton.
     * This manager handles user preferences and app settings.
     */
    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context,
        themeManager: ThemeManager,
        languageManager: LanguageManager
    ): SettingsManager {
        return SettingsManager(context, themeManager, languageManager)
    }
    
    /**
     * Provides the DrawerManager as a singleton.
     * This manager handles navigation drawer functionality.
     */
    @Provides
    @Singleton
    fun provideDrawerManager(
        themeManager: ThemeManager,
        languageManager: LanguageManager
    ): DrawerManager {
        return DrawerManager(themeManager, languageManager)
    }
} 