package com.sonuverma.deeploader

import com.sonuverma.deeploader.core.extraction.NewPipeDownloaderImpl
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

class ExtractionTest {
    @Test
    fun testExtractionWorkflow() = runBlocking {
        println("Starting Extraction Workflow Test...")
        
        // Initialize NewPipe directly
        NewPipe.init(NewPipeDownloaderImpl.getInstance())

        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        println("Extracting URL: $url")
        
        val streamInfo = StreamInfo.getInfo(url)
        
        println("==============================")
        println("Extraction Successful!")
        println("Title: ${streamInfo.name}")
        println("Uploader: ${streamInfo.uploaderName}")
        println("Video Streams Found: ${streamInfo.videoStreams?.size ?: 0}")
        println("Audio Streams Found: ${streamInfo.audioStreams?.size ?: 0}")
        println("==============================")
        
        assertTrue("Title should not be empty", streamInfo.name?.isNotEmpty() == true)
        assertTrue("Should have video streams", streamInfo.videoStreams?.isNotEmpty() == true)
        
        // Print best video stream
        val bestVideo = streamInfo.videoStreams?.maxByOrNull { it.getResolution()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 }
        println("Best Video Stream: ${bestVideo?.getResolution()} - ${bestVideo?.getFormat()?.name} (${bestVideo?.content?.take(30)}...)")
        
        println("Workflow verified: Streams are successfully extracted and ready for DownloadManager to start download!")
    }
}
