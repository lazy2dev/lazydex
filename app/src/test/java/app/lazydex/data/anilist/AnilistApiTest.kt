package app.lazydex.data.anilist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnilistApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun graphQLRequest_serializesNumericAndBooleanVariablesAsPrimitives() {
        val query = "query (${'$'}page: Int, ${'$'}userName: String) { Page { mediaList { id } } }"
        val variables = mapOf(
            "page" to JsonPrimitive(1),
            "userName" to JsonPrimitive("testuser"),
            "type" to JsonPrimitive("ANIME")
        )

        val request = GraphQLRequest(query, variables)
        val jsonString = json.encodeToString(GraphQLRequest.serializer(), request)

        assertTrue("page should be numeric JSON primitive", jsonString.contains("\"page\":1"))
        assertTrue("userName should be string JSON primitive", jsonString.contains("\"userName\":\"testuser\""))
        assertTrue("type should be string JSON primitive", jsonString.contains("\"type\":\"ANIME\""))
        assertFalse("page should NOT be string-wrapped", jsonString.contains("\"page\":\"1\""))
    }

    @Test
    fun pageMediaListResponse_parsesPageDataAndGraphQLErrors() {
        val jsonResponse = """
            {
              "data": {
                "Page": {
                  "pageInfo": {
                    "hasNextPage": true
                  },
                  "mediaList": [
                    {
                      "id": 12345,
                      "mediaId": 999,
                      "status": "CURRENT",
                      "progress": 12,
                      "media": {
                        "id": 999,
                        "title": {
                          "userPreferred": "Attack on Titan"
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(PageMediaListResponse.serializer(), jsonResponse)
        assertTrue(parsed.data?.Page?.pageInfo?.hasNextPage == true)
        assertEquals(1, parsed.data?.Page?.mediaList?.size)
        assertEquals("Attack on Titan", parsed.data?.Page?.mediaList?.first()?.media?.title?.userPreferred)
    }

    @Test
    fun pageMediaListResponse_parsesGraphQLErrorMessage() {
        val jsonResponse = """
            {
              "errors": [
                {
                  "message": "Invalid token"
                }
              ],
              "data": null
            }
        """.trimIndent()

        val parsed = json.decodeFromString(PageMediaListResponse.serializer(), jsonResponse)
        assertFalse(parsed.errors.isNullOrEmpty())
        assertEquals("Invalid token", parsed.errors!!.first().message)
    }
}
