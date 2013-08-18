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
package com.datatorrent.demos.ads;

import com.datatorrent.api.StreamingApplication;
import com.datatorrent.api.DAG;
import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.Context.PortContext;
import com.datatorrent.api.Operator.InputPort;
import com.datatorrent.lib.io.ConsoleOutputOperator;
import com.datatorrent.lib.io.HdfsOutputOperator;
import com.datatorrent.lib.io.PubSubWebSocketOutputOperator;
import com.datatorrent.lib.math.MarginMap;
import com.datatorrent.lib.math.QuotientMap;
import com.datatorrent.lib.math.SumCountMap;
import com.datatorrent.lib.stream.StreamMerger;
import com.datatorrent.lib.testbench.EventClassifier;
import com.datatorrent.lib.testbench.EventGenerator;
import com.datatorrent.lib.testbench.FilteredEventClassifier;
import com.datatorrent.lib.testbench.ThroughputCounter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;

/**
 * Example of application configuration for an ads demo<p>
 *
 * @since 0.3.2
 */
public class Application implements StreamingApplication
{
  public static final int WINDOW_SIZE_MILLIS = 500;
  public static final String P_numGenerators = Application.class.getName() + ".numGenerators";
  public static final String P_generatorVTuplesBlast = Application.class.getName() + ".generatorVTuplesBlast";
  public static final String P_generatorMaxWindowsCount = Application.class.getName() + ".generatorMaxWindowsCount";
  public static final String P_allInline = Application.class.getName() + ".allInline";
  public static final String P_enableHdfs = Application.class.getName() + ".enableHdfs";
  // adjust these depending on execution mode (junit, cli-local, cluster)
  private int applicationWindow = 5 * 1000 / WINDOW_SIZE_MILLIS;
  private int generatorVTuplesBlast = 1000;
  private int generatorMaxWindowsCount = 100;
  private int generatorWindowCount = 1;
  private boolean allInline = false;
  private int numGenerators = 1;

  public void setUnitTestMode()
  {
    generatorVTuplesBlast = 10;
    generatorWindowCount = 5;
    generatorMaxWindowsCount = 20;
    applicationWindow = 5;
  }

  public void setLocalMode()
  {
    generatorVTuplesBlast = 1000; // keep low to not distort window boundaries
    //generatorVTuplesBlast = 500000;
    generatorWindowCount = 5;
    //generatorMaxWindowsCount = 50;
    generatorMaxWindowsCount = 1000000;
  }

  private void configure(DAG dag, Configuration conf)
  {

    if (LAUNCHMODE_YARN.equals(conf.get(DAG.LAUNCH_MODE))) {
      setLocalMode();
      // settings only affect distributed mode
      dag.getAttributes().attr(DAG.CONTAINER_MEMORY_MB).setIfAbsent(2048);
      dag.getAttributes().attr(DAG.MASTER_MEMORY_MB).setIfAbsent(1024);
      dag.getAttributes().attr(DAG.CONTAINERS_MAX_COUNT).setIfAbsent(1);
    }
    else if (LAUNCHMODE_LOCAL.equals(conf.get(DAG.LAUNCH_MODE))) {
      setLocalMode();
    }

    this.generatorVTuplesBlast = conf.getInt(P_generatorVTuplesBlast, this.generatorVTuplesBlast);
    this.generatorMaxWindowsCount = conf.getInt(P_generatorMaxWindowsCount, this.generatorMaxWindowsCount);
    this.allInline = conf.getBoolean(P_allInline, this.allInline);
    this.numGenerators = conf.getInt(P_numGenerators, this.numGenerators);

  }

  private InputPort<Object> getConsolePort(DAG b, String name, boolean silent)
  {
    // output to HTTP server when specified in environment setting
    String daemonAddress = b.attrValue(DAG.DAEMON_ADDRESS, null);
    if (!StringUtils.isEmpty(daemonAddress)) {
      URI uri = URI.create("ws://" + daemonAddress + "/pubsub");
      String topic = "demos.ads." + name;
      //LOG.info("WebSocket with daemon at: {}", daemonAddress);
      PubSubWebSocketOutputOperator<Object> wsOut = b.addOperator(name, new PubSubWebSocketOutputOperator<Object>());
      wsOut.setUri(uri);
      wsOut.setTopic(topic);
      return wsOut.input;
    }
    ConsoleOutputOperator oper = b.addOperator(name, new ConsoleOutputOperator());
    oper.setStringFormat(name + "%s");
    oper.silent = silent;
    return oper.input;
  }

  public SumCountMap<String, Double> getSumOperator(String name, DAG b)
  {
    return b.addOperator(name, new SumCountMap<String, Double>());
  }

  public StreamMerger<HashMap<String, Integer>> getStreamMerger(String name, DAG b)
  {
    StreamMerger<HashMap<String, Integer>> oper = b.addOperator(name, new StreamMerger<HashMap<String, Integer>>());
    return oper;
  }

  public ThroughputCounter<String, Integer> getThroughputCounter(String name, DAG b)
  {
    ThroughputCounter<String, Integer> oper = b.addOperator(name, new ThroughputCounter<String, Integer>());
    oper.setRollingWindowCount(5);
    return oper;
  }

  public MarginMap<String, Double> getMarginOperator(String name, DAG b)
  {
    MarginMap<String, Double> oper = b.addOperator(name, new MarginMap<String, Double>());
    oper.setPercent(true);
    return oper;
  }

  public QuotientMap<String, Integer> getQuotientOperator(String name, DAG b)
  {
    QuotientMap<String, Integer> oper = b.addOperator(name, new QuotientMap<String, Integer>());
    oper.setMult_by(100);
    oper.setCountkey(true);
    return oper;
  }

  public EventGenerator getPageViewGenOperator(String name, DAG b)
  {
    EventGenerator oper = b.addOperator(name, EventGenerator.class);
    oper.setKeys("home,finance,sports,mail");
    // Paying $2.15,$3,$1.75,$.6 for 1000 views respectively
    oper.setValues("0.00215,0.003,0.00175,0.0006");
    oper.setWeights("25,25,25,25");
    oper.setTuplesBlast(this.generatorVTuplesBlast);
    oper.setMaxCountOfWindows(generatorMaxWindowsCount);
    oper.setRollingWindowCount(this.generatorWindowCount);
    return oper;
  }

  public EventClassifier getAdViewsStampOperator(String name, DAG b)
  {
    EventClassifier oper = b.addOperator(name, EventClassifier.class);
    HashMap<String, Double> kmap = new HashMap<String, Double>();
    kmap.put("sprint", null);
    kmap.put("etrade", null);
    kmap.put("nike", null);
    oper.setKeyMap(kmap);
    return oper;
  }

  public FilteredEventClassifier<Double> getInsertClicksOperator(String name, DAG b)
  {
    FilteredEventClassifier<Double> oper = b.addOperator(name, new FilteredEventClassifier<Double>());
    HashMap<String, Double> kmap = new HashMap<String, Double>();
    // Getting $1,$5,$4 per click respectively
    kmap.put("sprint", 1.0);
    kmap.put("etrade", 5.0);
    kmap.put("nike", 4.0);
    oper.setKeyMap(kmap);

    HashMap<String, ArrayList<Integer>> wmap = new HashMap<String, ArrayList<Integer>>();
    ArrayList<Integer> alist = new ArrayList<Integer>(3);
    alist.add(60);
    alist.add(10);
    alist.add(30);
    wmap.put("home", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(10);
    alist.add(75);
    alist.add(15);
    wmap.put("finance", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(10);
    alist.add(10);
    alist.add(80);
    wmap.put("sports", alist);
    alist = new ArrayList<Integer>(3);
    alist.add(50);
    alist.add(15);
    alist.add(35);
    wmap.put("mail", alist);
    oper.setKeyWeights(wmap);
    oper.setPassFilter(40);
    oper.setTotalFilter(1000);
    return oper;
  }

  @Override
  public void populateDAG(DAG dag, Configuration conf)
  {
    configure(dag, conf);
    dag.setAttribute(DAG.APPLICATION_NAME, "AdsDevApplication");
    dag.setAttribute(DAG.STREAMING_WINDOW_SIZE_MILLIS, WINDOW_SIZE_MILLIS); // set the streaming window size to 1 millisec

    //dag.getAttributes().attr(DAG.CONTAINERS_MAX_COUNT).setIfAbsent(9);
    EventGenerator viewGen = getPageViewGenOperator("viewGen", dag);
    dag.getMeta(viewGen).getAttributes().attr(OperatorContext.INITIAL_PARTITION_COUNT).set(numGenerators);
    dag.setOutputPortAttribute(viewGen.hash_data, PortContext.QUEUE_CAPACITY, 32 * 1024);

    EventClassifier adviews = getAdViewsStampOperator("adviews", dag);
    dag.setOutputPortAttribute(adviews.data, PortContext.QUEUE_CAPACITY, 32 * 1024);
    dag.setInputPortAttribute(adviews.event, PortContext.QUEUE_CAPACITY, 32 * 1024);

    FilteredEventClassifier<Double> insertclicks = getInsertClicksOperator("insertclicks", dag);
    dag.setInputPortAttribute(insertclicks.data, PortContext.QUEUE_CAPACITY, 32 * 1024);

    SumCountMap<String, Double> viewAggregate = getSumOperator("viewAggr", dag);
    dag.setAttribute(viewAggregate, OperatorContext.APPLICATION_WINDOW_COUNT, applicationWindow);
    dag.setInputPortAttribute(viewAggregate.data, PortContext.QUEUE_CAPACITY, 32 * 1024);

    SumCountMap<String, Double> clickAggregate = getSumOperator("clickAggr", dag);
    dag.setAttribute(clickAggregate, OperatorContext.APPLICATION_WINDOW_COUNT, applicationWindow);

    dag.setInputPortAttribute(adviews.event, PortContext.PARTITION_PARALLEL, true);
    dag.addStream("views", viewGen.hash_data, adviews.event).setInline(true);
    dag.setInputPortAttribute(insertclicks.data, PortContext.PARTITION_PARALLEL, true);
    dag.setInputPortAttribute(viewAggregate.data, PortContext.PARTITION_PARALLEL, true);
    DAG.StreamMeta viewsAggStream = dag.addStream("viewsaggregate", adviews.data, insertclicks.data, viewAggregate.data).setInline(true);

    if (conf.getBoolean(P_enableHdfs, false)) {
      HdfsOutputOperator viewsToHdfs = dag.addOperator("viewsToHdfs", new HdfsOutputOperator());
      viewsToHdfs.setAppend(false);
      viewsToHdfs.setFilePath("file:///tmp/adsdemo/views-%(operatorId)-part%(partIndex)");
      dag.setInputPortAttribute(viewsToHdfs.input, PortContext.PARTITION_PARALLEL, true);
      viewsAggStream.addSink(viewsToHdfs.input);
    }

    dag.setInputPortAttribute(clickAggregate.data, PortContext.PARTITION_PARALLEL, true);
    dag.addStream("clicksaggregate", insertclicks.filter, clickAggregate.data).setInline(true);


    QuotientMap<String, Integer> ctr = getQuotientOperator("ctr", dag);
    SumCountMap<String, Double> cost = getSumOperator("cost", dag);
    SumCountMap<String, Double> revenue = getSumOperator("rev", dag);
    MarginMap<String, Double> margin = getMarginOperator("margin", dag);
    StreamMerger<HashMap<String, Integer>> merge = getStreamMerger("countmerge", dag);
    ThroughputCounter<String, Integer> tuple_counter = getThroughputCounter("tuple_counter", dag);

    dag.addStream("adviewsdata", viewAggregate.sum, cost.data);
    dag.addStream("clicksdata", clickAggregate.sum, revenue.data);
    dag.addStream("viewtuplecount", viewAggregate.count, ctr.denominator, merge.data1).setInline(allInline);
    dag.addStream("clicktuplecount", clickAggregate.count, ctr.numerator, merge.data2).setInline(allInline);
    dag.addStream("total count", merge.out, tuple_counter.data).setInline(allInline);

    InputPort<Object> revconsole = getConsolePort(dag, "revConsole", false);
    InputPort<Object> costconsole = getConsolePort(dag, "costConsole", false);
    InputPort<Object> marginconsole = getConsolePort(dag, "marginConsole", false);
    InputPort<Object> ctrconsole = getConsolePort(dag, "ctrConsole", false);
    InputPort<Object> viewcountconsole = getConsolePort(dag, "viewCountConsole", false);

    dag.addStream("revenuedata", revenue.sum, margin.denominator, revconsole).setInline(allInline);
    dag.addStream("costdata", cost.sum, margin.numerator, costconsole).setInline(allInline);
    dag.addStream("margindata", margin.margin, marginconsole).setInline(allInline);
    dag.addStream("ctrdata", ctr.quotient, ctrconsole).setInline(allInline);
    dag.addStream("tuplecount", tuple_counter.count, viewcountconsole).setInline(allInline);

  }

}
