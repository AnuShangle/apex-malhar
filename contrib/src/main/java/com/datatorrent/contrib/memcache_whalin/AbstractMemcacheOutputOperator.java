/*
 * Copyright (c) 2013 Malhar Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.memcache_whalin;

import com.datatorrent.api.BaseOperator;
import com.datatorrent.api.Context.OperatorContext;
import com.whalin.MemCached.SockIOPool;
import com.whalin.MemCached.MemCachedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memcache output adapter operator, which produce data to Memcached using whalin library.<p><br>
 *
 * <br>
 * Ports:<br>
 * <b>Input</b>: Can have any number of input ports<br>
 * <b>Output</b>: no output port<br>
 * <br>
 * Properties:<br>
 * <b>servers</b>:the memcache server url list<br>
 * <b>pool</b>:memcache sockIOPool<br>
 * <b>mcc</b>:MemCachedClient instance<br>
 * <br>
 * Compile time checks:<br>
 * None<br>
 * <br>
 * Run time checks:<br>
 * None<br>
 * <br>
 * <b>Benchmarks</b>:TBD
 * <br>
 *
 * @since 0.3.2
 */
public class AbstractMemcacheOutputOperator extends BaseOperator
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractMemcacheOutputOperator.class);
  String[] servers;
  private transient SockIOPool pool;
  protected transient MemCachedClient mcc;

  @Override
  public void setup(OperatorContext context)
  {
    pool = SockIOPool.getInstance();
    pool.setServers( servers );
    //		pool.setWeights( weights );

    // set some basic pool settings
    // 5 initial, 5 min, and 250 max conns
    // and set the max idle time for a conn
    // to 6 hours
    pool.setInitConn( 5 );
    pool.setMinConn( 5 );
    pool.setMaxConn( 250 );
    pool.setMaxIdle( 1000 * 60 * 60 * 6 );

    // set the sleep for the maint thread
    // it will wake up every x seconds and
    // maintain the pool size
    pool.setMaintSleep( 30 );

    // set some TCP settings
    // disable nagle
    // set the read timeout to 3 secs
    // and don't set a connect timeout
    pool.setNagle( false );
    pool.setSocketTO( 3000 );
    pool.setSocketConnectTO( 0 );

    pool.initialize();

    mcc = new MemCachedClient();
  }

  public void setServers(String[] servers)
  {
    this.servers = servers;
  }

  @Override
  public void teardown()
  {
  }

}
