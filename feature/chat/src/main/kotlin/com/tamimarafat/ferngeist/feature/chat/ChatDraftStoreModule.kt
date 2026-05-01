package com.tamimarafat.ferngeist.feature.chat

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatDraftStoreModule {

    @Binds
    @Singleton
    abstract fun bindChatDraftStore(
        impl: SharedPreferencesChatDraftStore,
    ): ChatDraftStore
}
