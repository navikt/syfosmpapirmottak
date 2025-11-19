package no.nav.syfo.application

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.getEnvVar
import no.nav.syfo.module

fun createApplicationEngine():
    EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(
        factory = Netty,
        port = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        module = Application::module
    )
