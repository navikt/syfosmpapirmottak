package no.nav.syfo.util

import no.nav.syfo.ApplicationConfig
import no.nav.syfo.VaultCredentials
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

fun readProducerConfig(
    config: ApplicationConfig,
    credentials: VaultCredentials,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = valueSerializer
) = Properties().apply {
    load(ApplicationConfig::class.java.getResourceAsStream("/kafka_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.deserializer"] = keySerializer.qualifiedName
    this["value.deserializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = config.kafkaBootstrapServers
}