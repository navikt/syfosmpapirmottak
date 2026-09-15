package no.nav.syfo.service

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.sykmelding.api.SykmeldingOcrParser
import no.nav.sykmelding.api.SykmeldingResult

/**
 * Kjører OCR-parsing i prosess via biblioteket `no.nav.sykmelding:engine-tesseract`, som erstatning
 * for det tidligere HTTP-kallet mot den frittstående `sykmelding-ocr-service`.
 *
 * [parser] er stateful og trådsikres ved at hver parse-metode er `@Synchronized` internt (én parse
 * om gangen). Det er tilstrekkelig for shadow-flyten som har lavt volum. Parsingen er CPU-tung og
 * blokkerende, så den kjøres på [Dispatchers.IO].
 *
 * Instansen eier [SykmeldingOcrParser] (som er [Closeable]) og lukkes ved shutdown.
 */
class OcrParserService(private val parser: SykmeldingOcrParser) : Closeable {

    suspend fun parse(pdfBytes: ByteArray): List<OcrParserParseResponse> =
        withContext(Dispatchers.IO) { parser.parseBundle(pdfBytes).map { it.toResponse() } }

    private fun SykmeldingResult.toResponse(): OcrParserParseResponse =
        when (this) {
            is SykmeldingResult.Success -> OcrParserParseResponse.Success(sykmelding)
            is SykmeldingResult.Unsupported -> OcrParserParseResponse.Unsupported(reason)
            is SykmeldingResult.Failure -> OcrParserParseResponse.Failure(reason)
        }

    override fun close() {
        parser.close()
    }
}
