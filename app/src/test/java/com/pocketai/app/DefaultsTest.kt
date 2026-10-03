package com.pocketai.app
import com.pocketai.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
class DefaultsTest {@Test fun s25DefaultsAreConservative(){val s=AppSettings();assertEquals(4096,s.contextSize);assertEquals(1024,s.maxTokens);assertTrue(s.temperature in 0f..2f);assertTrue(s.topP in 0f..1f)}@Test fun byteFormatterWorks(){assertEquals("1.00 GB",formatBytes(1L shl 30));assertEquals("512.0 MB",formatBytes(512L shl 20))}}
