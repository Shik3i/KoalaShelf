package net.koalastuff.koalashelf.di

import net.koalastuff.koalashelf.core.data.local.KoalaDatabase
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.repository.DownloadRepository
import net.koalastuff.koalashelf.core.data.repository.LibraryRepository
import net.koalastuff.koalashelf.core.data.repository.ProgressRepository
import net.koalastuff.koalashelf.core.data.repository.QueueRepository
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.network.AbsLiveSocket
import net.koalastuff.koalashelf.core.network.createDefaultHttpClient
import net.koalastuff.koalashelf.core.player.KoalaPlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koalaAppModule = module {
    single { createDefaultHttpClient() }
    single { AbsLiveSocket(get()) }
    single<KoalaDatabase> { KoalaDatabase.build(androidContext()) }
    single { get<KoalaDatabase>().koalaDao() }
    single { PrefsRepository(androidContext()) }
    single { SessionRepository(androidContext(), get(), get()) }
    single { ProgressRepository(get(), get()) }
    single { QueueRepository(get(), get()) }
    single { DownloadRepository(androidContext(), get(), get()) }
    single { LibraryRepository(get(), get(), get()) }
    single {
        KoalaPlayerController(
            context = androidContext(),
            sessionRepository = get(),
            prefsRepository = get(),
            progressRepository = get(),
            queueRepository = get(),
            downloadRepository = get(),
            libraryRepository = get(),
        )
    }
}
