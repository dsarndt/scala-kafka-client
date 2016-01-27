package cakesolutions.kafka.testkit

import java.net.ServerSocket
import java.util.Properties

import kafka.server.{KafkaConfig, KafkaServerStartable}
import org.apache.curator.test.TestingServer
import org.slf4j.LoggerFactory

object KafkaServer {

  /**
   * Choose an available port
   */
  protected def choosePort(): Int = choosePorts(1).head

  /**
   * Choose a number of random available ports
   */
  protected def choosePorts(count: Int): List[Int] = {
    val sockets =
      for (i <- 0 until count)
        yield new ServerSocket(0)
    val socketList = sockets.toList
    val ports = socketList.map(_.getLocalPort)
    socketList.foreach(_.close())
    ports
  }

  /**
   * Create a test config for the given node id
   */
  private def brokerConfig(zkConnect: String, enableControlledShutdown: Boolean = true): ((Int, Int)) => Properties = {
    case (port, node) => createBrokerConfig(node, port, zkConnect, enableControlledShutdown)
  }

  /**
   * Create a test config for the given node id
   */
  private def createBrokerConfig(nodeId: Int, port: Int = choosePort(), zookeeperConnect: String,
                                 enableControlledShutdown: Boolean = true): Properties = {
    val props = new Properties
    props.put("broker.id", nodeId.toString)
    props.put("host.name", "localhost")
    props.put("port", port.toString)
    props.put("log.dir", "./target/kafka")
    props.put("zookeeper.connect", zookeeperConnect)
    props.put("replica.socket.timeout.ms", "1500")
    props.put("controlled.shutdown.enable", enableControlledShutdown.toString)
    props
  }

  protected def portConfig(zkConnect: String, enableControlledShutdown: Boolean = true): ((Int, Int)) => KafkaConfig = brokerConfig(zkConnect, enableControlledShutdown) andThen KafkaConfig.apply

  protected def randomConfigs(num: Int, zkConnect: String, enableControlledShutdown: Boolean = true): List[KafkaConfig] =
    choosePorts(num).zipWithIndex.map(portConfig(zkConnect, enableControlledShutdown))
}

//A startable kafka server.  zookeeperPort is generated.
class KafkaServer(val kafkaPort: Int = KafkaServer.choosePort(), val zookeeperPort: Int = KafkaServer.choosePort()) {

  import KafkaServer._

  val log = LoggerFactory.getLogger(getClass)
  val zookeeperConnect = "127.0.0.1:" + zookeeperPort

  //Start a zookeeper server
  val zkServer = new TestingServer(zookeeperPort)

  //Build Kafka config with zookeeper connection
  val config = portConfig(zkServer.getConnectString)((kafkaPort,1))
  log.info("ZK Connect: " + zkServer.getConnectString)

  // Kafka Test Server
  val kafkaServer = new KafkaServerStartable(config)

  def startup() = {
    kafkaServer.startup()
    log.info(s"Started kafka on port [${kafkaPort}]")
  }

  def close() = {
    log.info(s"Stopping kafka on port [${kafkaPort}")
    kafkaServer.shutdown()
    zkServer.stop()
  }
}