package tss.t.tsiptv.core.database

import tss.t.tsiptv.TSAndroidApplication
import tss.t.tsiptv.core.network.AndroidNetworkClientProvider
import tss.t.tsiptv.core.network.NetworkClient

actual fun createDatabaseFactory(networkClient: NetworkClient): DatabaseFactory {
    return AndroidDatabaseFactory.create(TSAndroidApplication.instance, networkClient)
}
