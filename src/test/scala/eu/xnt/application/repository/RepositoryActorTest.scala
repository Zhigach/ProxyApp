package eu.xnt.application.repository

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import eu.xnt.application.model.CandleModels.{CandleResponse, HistoryRequest}
import eu.xnt.application.repository.testutils.Util.randomQuote
import org.scalatest.BeforeAndAfterAll
import akka.util.Timeout
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class RepositoryActorTest
    extends TestKit(ActorSystem("RepositoryActorTest"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val inMemoryCandleRepository = InMemoryCandleRepository(60000)
    private val repositoryActor = ActorSystem()
      .actorOf(Props.create(classOf[RepositoryActor], inMemoryCandleRepository), "TestRepositoryActor")

    private val ts = (System.currentTimeMillis() / 60000 - 2) * 60000 + 1

    "RepositoryActor" must {

        "save quote" in {
            repositoryActor ! randomQuote(ts, "TEST")
            expectNoMessage(100 millis)
            assert(inMemoryCandleRepository.bufferSize("TEST") == 1)
            assert(inMemoryCandleRepository.bufferSize("TST") == 0)
        }

        "return historical quotes when requested" in {
            implicit val timeout: Timeout = Timeout(100 millis)
            repositoryActor ? HistoryRequest(2) onComplete {
                case Success(candles) =>
                    candles match {
                        case CandleResponse(candles) =>
                            assert(candles.length == 1)
                    }
                case Failure(_) => fail("Response is not received")
            }
        }

        "save quote into new candle" in {
            repositoryActor ! randomQuote(ts + 60001, "TEST")
            expectNoMessage(1000 millis)
            assert(inMemoryCandleRepository.bufferSize("TEST") == 2)
        }

        "return all historical quotes" in {
            implicit val timeout: Timeout = Timeout(100 millis)
            repositoryActor ? HistoryRequest(2) onComplete {
                case Success(candles) =>
                    candles match {
                        case CandleResponse(candles) =>
                            assert(candles.length == 2)
                    }
                case Failure(_) => fail("Response is not received")
            }
        }

        "return historical quotes only with requested depth" in {
            implicit val timeout: Timeout = Timeout(100 millis)
            repositoryActor ? HistoryRequest(1) onComplete {
                case Success(candles) =>
                    candles match {
                        case CandleResponse(candles) =>
                            assert(candles.length == 1)
                    }
                case Failure(_) => fail("Response is not received")
            }
        }
    }

    override def afterAll(): Unit = {
        TestKit.shutdownActorSystem(system)
    }

}
