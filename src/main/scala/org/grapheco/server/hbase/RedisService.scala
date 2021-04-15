package org.grapheco.server.hbase

import com.redis.{RedisClient, RedisClientPool}

import scala.reflect.ClassTag

/**
 * @ClassName RedisServer
 * @Description TODO
 * @Author huchuan
 * @Date 2020/11/9
 * @Version 0.1
 */

object RedisService {
  private val redisHost: String = "10.0.90.206"
  private val redisPort: Int = 6379
  private val redisTimeOut: Int = 30000 // ms
  private val maxTotal = 300
  private val maxIdle = 100
  private val minIdle = 1
  private val secret = "huchuan"

  val clients = new RedisClientPool(
    redisHost, redisPort, maxIdle = maxIdle,
    timeout = redisTimeOut, secret = Option(secret))


  def execute[T](op: RedisClient=>T):T={
    clients.withClient{
      client => {
        op(client)
      }
    }
  }

  def set(key:String, value:Any) = {
    clients.withClient{
      client => {
        client.set(key, value)
      }
    }
  }

  def get(key:String)= {
    clients.withClient{
      client => {
        client.get(key)
      }
    }
  }


}
