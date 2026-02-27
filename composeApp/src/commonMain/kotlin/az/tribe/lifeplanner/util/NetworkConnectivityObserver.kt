@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

expect class NetworkConnectivityObserver() {
    val isConnected: StateFlow<Boolean>
    fun observe(): Flow<Boolean>
}
