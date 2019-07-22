/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package kafka.manager.utils

import java.util.{Collections, Locale, Properties}

import scala.collection.JavaConverters._
import kafka.api.ApiVersion
//import kafka.manager.utils.TopicConfigs
import kafka.message.BrokerCompressionCodec
import kafka.server.{KafkaConfig, ThrottledReplicaListValidator}
import org.apache.kafka.common.errors.InvalidConfigurationException
import org.apache.kafka.common.config.{AbstractConfig, ConfigDef}
import org.apache.kafka.common.record.{LegacyRecord, TimestampType}

import scala.collection.mutable
import org.apache.kafka.common.config.ConfigDef.{ConfigKey, ValidList, Validator}

object AdditionalDefaults {
  val CompressionType = "Default Owner Name"
}

case class AdditionalConfig(props: java.util.Map[_, _]) extends AbstractConfig(AdditionalConfig.configDef, props, false) {
  /**
    * Important note: Any configuration parameter that is passed along from KafkaConfig to AdditionalConfig
    * should also go in kafka.server.KafkaServer.copyKafkaConfigToLog.
    */
  val compressionType = getString(AdditionalConfig.CompressionTypeProp).toLowerCase(Locale.ROOT)
}

object AdditionalConfig extends TopicConfigs {

  def main(args: Array[String]) {
    println(configDef.toHtmlTable)
  }

  val CompressionTypeProp = "Owner"//TopicConfig.COMPRESSION_TYPE_CONFIG

  // Leave these out of TopicConfig for now as they are replication quota configs
  //val LeaderReplicationThrottledReplicasProp = "leader.replication.throttled.replicas"
  //val FollowerReplicationThrottledReplicasProp = "follower.replication.throttled.replicas"

  val CompressionTypeDoc = "Topic's owner name"//TopicConfig.COMPRESSION_TYPE_DOC
/*
  val LeaderReplicationThrottledReplicasDoc = "A list of replicas for which log replication should be throttled on " +
    "the leader side. The list should describe a set of replicas in the form " +
    "[PartitionId]:[BrokerId],[PartitionId]:[BrokerId]:... or alternatively the wildcard '*' can be used to throttle " +
    "all replicas for this topic."
  val FollowerReplicationThrottledReplicasDoc = "A list of replicas for which log replication should be throttled on " +
    "the follower side. The list should describe a set of " + "replicas in the form " +
    "[PartitionId]:[BrokerId],[PartitionId]:[BrokerId]:... or alternatively the wildcard '*' can be used to throttle " +
    "all replicas for this topic." */

  private class AdditionalConfigDef extends ConfigDef {

    private final val serverDefaultConfigNames = mutable.Map[String, String]()

    def define(name: String, defType: ConfigDef.Type, defaultValue: Any, validator: Validator,
               importance: ConfigDef.Importance, doc: String, serverDefaultConfigName: String): AdditionalConfigDef = {
      super.define(name, defType, defaultValue, validator, importance, doc)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    def define(name: String, defType: ConfigDef.Type, defaultValue: Any, importance: ConfigDef.Importance,
               documentation: String, serverDefaultConfigName: String): AdditionalConfigDef = {
      super.define(name, defType, defaultValue, importance, documentation)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    def define(name: String, defType: ConfigDef.Type, importance: ConfigDef.Importance, documentation: String,
               serverDefaultConfigName: String): AdditionalConfigDef = {
      super.define(name, defType, importance, documentation)
      serverDefaultConfigNames.put(name, serverDefaultConfigName)
      this
    }

    override def headers = List("Name", "Description", "Type", "Default", "Valid Values", "Server Default Property", "Importance").asJava

    override def getConfigValue(key: ConfigKey, headerName: String): String = {
      headerName match {
        case "Server Default Property" => serverDefaultConfigNames.get(key.name).get
        case _ => super.getConfigValue(key, headerName)
      }
    }

    def serverConfigName(configName: String): Option[String] = serverDefaultConfigNames.get(configName)
  }

  private val configDef: AdditionalConfigDef = {
    import org.apache.kafka.common.config.ConfigDef.Importance._
    import org.apache.kafka.common.config.ConfigDef.Range._
    import org.apache.kafka.common.config.ConfigDef.Type._
    import org.apache.kafka.common.config.ConfigDef.ValidString._

    new AdditionalConfigDef()
      .define(CompressionTypeProp, STRING, AdditionalDefaults.CompressionType, in(BrokerCompressionCodec.brokerCompressionOptions:_*),
        MEDIUM, CompressionTypeDoc, KafkaConfig.CompressionTypeProp)
  }

  def apply(): AdditionalConfig = AdditionalConfig(new Properties())

  def configNames: Seq[String] = configDef.names.asScala.toSeq.sorted

  def serverConfigName(configName: String): Option[String] = configDef.serverConfigName(configName)

  /**
    * Create a log config instance using the given properties and defaults
    */
  def fromProps(defaults: java.util.Map[_ <: Object, _ <: Object], overrides: Properties): AdditionalConfig = {
    val props = new Properties()
    props.putAll(defaults)
    props.putAll(overrides)
    AdditionalConfig(props)
  }

  /**
    * Check that property names are valid
    */
  def validateNames(props: Properties) {
    val names = configNames
    for(name <- props.asScala.keys)
      if (!names.contains(name))
        throw new InvalidConfigurationException(s"Unknown topic config name: $name")
  }

  /**
    * Check that the given properties contain only valid log config names and that all values can be parsed and are valid
    */
  def validate(props: Properties) {
    validateNames(props)
    configDef.parse(props)
  }

  def configNamesAndDoc: Seq[(String, String)] = {
    Option(configDef).fold {
      configNames.map(n => n -> "")
    } {
      configDef =>
        val keyMap = configDef.configKeys()
        configNames.map(n => n -> Option(keyMap.get(n)).map(_.documentation).flatMap(Option.apply).getOrElse(""))
    }
  }
}
