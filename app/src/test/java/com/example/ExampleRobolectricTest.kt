package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SysOpt", appName)
  }

  @Test
  fun `verify system status parser thermal parsing`() {
    val sampleThermal = "Current thermal status: 1\nSkin Temperature: 36.2°C"
    val parsed = com.example.data.shell.SystemStatusParser.parseThermalService(sampleThermal)
    assertEquals(1, parsed.statusLevel)
    assertEquals("Light Throttling", parsed.statusText)
  }

  @Test
  fun `verify standby bucket parser`() {
    val sampleBucket = "45 (restricted)"
    val parsed = com.example.data.shell.SystemStatusParser.parseStandbyBucket(sampleBucket)
    assertEquals("Restricted (45)", parsed)
  }
}
