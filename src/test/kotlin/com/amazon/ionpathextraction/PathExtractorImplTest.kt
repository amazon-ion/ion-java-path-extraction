package com.amazon.ionpathextraction

class PathExtractorImplTest : PathExtractorTest() {
    override fun <T> PathExtractorBuilder<T>.buildExtractor(): PathExtractor<T> = buildLegacy()
}
