# ── Stage 1: Install Tesseract and collect the full native .so closure ──
# Debian 13 (trixie) to match the java25-debian13 runtime ABI below.
FROM debian:trixie-slim AS native-builder

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      tesseract-ocr \
      tesseract-ocr-eng \
      tesseract-ocr-nor \
      binutils \
    && rm -rf /var/lib/apt/lists/*

# Collect libtesseract + libleptonica, then resolve all transitive .so deps via
# three passes of ldd. Excludes glibc/libm/ld-linux which are in every Linux
# container, and libstdc++/libgcc_s which are in distroless/java (cc base).
RUN mkdir /native-libs && \
    find /usr/lib /lib -name 'libtesseract.so*' \
                    -o -name 'libleptonica.so*' 2>/dev/null | \
      xargs -I{} cp -L {} /native-libs/ 2>/dev/null || true && \
    for pass in 1 2 3; do \
      for f in /native-libs/*.so*; do \
        ldd "$f" 2>/dev/null | grep '=> /' | awk '{print $3}' | \
          grep -vE 'libc\.so|libm\.so|libpthread|libdl\.so|ld-linux|libstdc\+\+|libgcc_s' | \
          xargs -I{} cp -nL {} /native-libs/ 2>/dev/null || true; \
      done; \
    done

# ── Stage 2: Distroless runtime ──
FROM gcr.io/distroless/java25-debian13@sha256:8ce26d023018ca2f11bf2530cd3a10a7fd8456c3142b5f9a7d6b135a1411c86a
WORKDIR /app

# libtesseract + all transitive image-processing libs (libpng, libjpeg, libtiff,
# libwebp, libopenjp2, libgif, libgomp, libarchive, libleptonica, ...) for the
# in-process OCR parser (no.nav.sykmelding:engine-tesseract).
COPY --from=native-builder /native-libs/ /usr/lib/x86_64-linux-gnu/

# Tesseract 5 LSTM tessdata — nor+eng for OCR, osd for orientation detection.
# OcrConfig.detectTessdataPath() auto-resolves TESSDATA_PREFIX at startup.
COPY --from=native-builder /usr/share/tesseract-ocr/5/tessdata/nor.traineddata /tessdata/
COPY --from=native-builder /usr/share/tesseract-ocr/5/tessdata/eng.traineddata /tessdata/
COPY --from=native-builder /usr/share/tesseract-ocr/5/tessdata/osd.traineddata /tessdata/

# JavaCV/OpenCV natives ship inside the javacv/opencv JARs on the classpath and
# are auto-extracted at startup — no separate COPY needed.
ENV TESSDATA_PREFIX=/tessdata

COPY build/install/*/lib /lib
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
# jna.library.path → libtesseract.so (tess4j loads via JNA, not java.library.path)
ENTRYPOINT ["java", \
    "--enable-native-access=ALL-UNNAMED", \
    "-Djna.library.path=/usr/lib/x86_64-linux-gnu", \
    "-Dlogback.configurationFile=logback.xml", \
    "-cp", "/lib/*", \
    "no.nav.syfo.BootstrapKt"]
