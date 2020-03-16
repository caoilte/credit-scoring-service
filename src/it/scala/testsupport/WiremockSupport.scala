package testsupport

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{ BeforeAndAfterAll, Suite }

trait WiremockSupport extends BeforeAndAfterAll {
  _: Suite =>

  protected val wiremockPort: Int = 3002

  protected val wiremockServer: WireMockServer = new WireMockServer(wireMockConfig().port(wiremockPort))

  override def beforeAll(): Unit = {
    wiremockServer.start()
    super.beforeAll()
  }

  override def afterAll: Unit = {
    super.afterAll()
    wiremockServer.stop()
  }
}
