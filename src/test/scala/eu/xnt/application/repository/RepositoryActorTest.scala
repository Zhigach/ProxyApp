package eu.xnt.application.repository

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import eu.xnt.application.UnitTestSpec
import eu.xnt.application.server.ProxyServer
import eu.xnt.application.server.ProxyServer.CandleHistory
import eu.xnt.application.testutils.Util.oldQuote

import scala.concurrent.duration._
import scala.language.postfixOps

class RepositoryActorTest extends UnitTestSpec {

    val testKit: ActorTestKit = ActorTestKit("RepositoryActorTest")

    private val inMemoryCandleRepository = new InMemoryCandleRepository(60000)
    private val repositoryActor = testKit.spawn(RepositoryActor(inMemoryCandleRepository), "TestRepositoryActor")
    private val testProbe = testKit.createTestProbe[ProxyServer.CandleHistory]("TestProbe")

    "RepositoryActor" should "save quote" in {
        repositoryActor ! RepositoryActor.AddQuote(oldQuote(2, "TEST"))
        testProbe.expectNoMessage(400 millis)
        inMemoryCandleRepository.bufferSize("TEST") shouldEqual 1
        inMemoryCandleRepository.bufferSize("TST") shouldEqual 0
    }

    it should "return historical quotes when requested" in {
        repositoryActor ! RepositoryActor.CandleHistoryRequest(2, testProbe.ref)
        val result = testProbe.expectMessageType[CandleHistory](1 second)
        result.candles should have length 1
    }

    it should "save quote into new candle" in {
        repositoryActor ! RepositoryActor.AddQuote(oldQuote(1, "TEST"))
        testProbe.expectNoMessage(400 millis) //TODO как бы избавиться от костыля?
        inMemoryCandleRepository.bufferSize("TEST") shouldEqual 2
    }

    it should "return all historical quotes" in {
        repositoryActor ! RepositoryActor.CandleHistoryRequest(2, testProbe.ref)
        val result = testProbe.expectMessageType[CandleHistory](1 second)
        result.candles should have length 2
    }

    it should "return historical quotes only with requested depth" in {
        repositoryActor ! RepositoryActor.CandleHistoryRequest(1, testProbe.ref)
        val result = testProbe.expectMessageType[CandleHistory](1 second)
        result.candles should have length 1
    }

    override def afterAll(): Unit = {
        testKit.shutdownTestKit()
    }

}
