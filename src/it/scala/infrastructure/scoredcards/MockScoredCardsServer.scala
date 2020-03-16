package infrastructure.scoredcards

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalToJson, post, urlPathMatching}
import com.github.tomakehurst.wiremock.http.HttpStatus
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.Json

import scala.io.Source

class MockScoredCardsServer(server: WireMockServer, basePath: String) {

  def loadResource(resource: String): String = {
    val source = Source.fromURL(getClass.getResource(resource), "UTF8")
    val result = source.mkString
    source.close()
    result
  }

  private val errorResponse: String =
    s"""
      {
        "message": "Error message"
      }
    """

  private def prepareResponse(resource: String, httpStatus: Int): String =
    if (HttpStatus.isSuccess(httpStatus)) {
      loadResource(resource)
    } else {
      errorResponse
    }

  def mockCardsResponse(httpStatus: Int, requestBody: Json, resource: String): StubMapping =
    server.stubFor(
      post(urlPathMatching(s"$basePath/v2/creditcards"))
        .withRequestBody(equalToJson(requestBody.toString()))
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
            .withHeader("content-type", "application/json")
            .withBody(prepareResponse(resource, httpStatus))
        )
    )
}
