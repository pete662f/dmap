package com.dmap.services.routing

import com.dmap.routing.RouteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaResponseParserTest {
    @Test
    fun `parseRoute returns success with summary and geometry`() {
        val payload =
            """
            {
              "trip": {
                "summary": {
                  "length": 3.42,
                  "time": 812.0
                },
                "legs": [
                  {
                    "shape": "gkeeiBwmb~VouSooB"
                  }
                ]
              }
            }
            """.trimIndent()

        val result = ValhallaResponseParser.parseRoute(payload)

        assertTrue(result is RouteResult.Success)
        val success = result as RouteResult.Success
        assertEquals(3.42, success.summary.distanceKilometers, 0.000001)
        assertEquals(812.0, success.summary.durationSeconds, 0.000001)
        assertEquals(2, success.path.coordinates.size)
    }

    @Test
    fun `parseFailure maps no route errors`() {
        val result = ValhallaResponseParser.parseFailure(
            payload = """{"error_code":442,"error":"No path could be found for input"}""",
            statusCode = 400,
        )

        assertEquals(RouteResult.NoRoute, result)
    }

    @Test
    fun `parseFailure maps generic backend errors`() {
        val result = ValhallaResponseParser.parseFailure(
            payload = """{"error":"worker timeout"}""",
            statusCode = 504,
        )

        assertTrue(result is RouteResult.Error)
        assertEquals("worker timeout", (result as RouteResult.Error).message)
    }
}
