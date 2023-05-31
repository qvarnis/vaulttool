package vaulttool

import se.su.it.vaulttool.VaultRestService
import se.su.it.vaulttool.schedule.TokenRenewer

class BootStrap {
    def grailsApplication
    def vaultRestService
    TokenRenewer tokenRenewer

    def init = { servletContext ->
        initializeVault()
        tokenRenewer.runTokenRenewer()
    }
    def destroy = {
    }

    def initializeVault() {
        vaultRestService.enableApproleAuth(grailsApplication.config.getProperty("vault.vaulttoken", String.class))
        //vaultRestService.putDefaultVaultToolPolicy(grailsApplication.config.vault.vaulttoken)
    }
}
