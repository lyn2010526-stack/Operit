package com.cynosure.operit.core.tools.agent

import com.cynosure.shower.IShowerService
import com.cynosure.showerclient.ShowerBinderRegistry as CoreShowerBinderRegistry

/**
 * App-level facade over the shared Shower client binder registry.
 */
object ShowerBinderRegistry {

    fun setService(newService: IShowerService?) {
        CoreShowerBinderRegistry.setService(newService)
    }

    fun getService(): IShowerService? = CoreShowerBinderRegistry.getService()

    fun hasAliveService(): Boolean = CoreShowerBinderRegistry.hasAliveService()
}
