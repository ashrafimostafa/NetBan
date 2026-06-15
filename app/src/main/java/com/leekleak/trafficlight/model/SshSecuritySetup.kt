package com.leekleak.trafficlight.model

import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object SshSecuritySetup {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }

            SecurityUtils.registerSecurityProvider(SecurityUtils.BOUNCY_CASTLE)
            SecurityUtils.setSecurityProvider(SecurityUtils.BOUNCY_CASTLE)

            initialized = true
        }
    }
}
