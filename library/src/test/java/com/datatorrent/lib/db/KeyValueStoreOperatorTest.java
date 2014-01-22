/*
 *  Copyright (c) 2012-2014 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.lib.db;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;

import com.datatorrent.api.BaseOperator;
import com.datatorrent.api.DAG;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.LocalMode;

/**
 *
 * @param <S>
 * @since 0.9.3
 */
public class KeyValueStoreOperatorTest<S extends KeyValueStore>
{
  protected S store;

  public KeyValueStoreOperatorTest(S store)
  {
    this.store = store;
  }

  public static class CollectorModule<T> extends BaseOperator
  {
    static Map<String, String> resultMap = new HashMap<String, String>();
    static long resultCount = 0;
    public final transient DefaultInputPort<T> inputPort = new DefaultInputPort<T>()
    {
      @Override
      public void process(T t)
      {
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>)t;
        resultMap.putAll(map);
        resultCount++;
      }

    };
  }

  protected static class InputOperator<S2 extends KeyValueStore> extends AbstractKeyValueStoreInputOperator<Map<String, String>, S2>
  {
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> convertToTuple(Map<Object, Object> o)
    {
      return (Map<String, String>)(Map<?, ?>)o;
    }

  }

  protected static class OutputOperator<S2 extends KeyValueStore> extends AbstractStoreOutputOperator<Map<String, String>, S2>
  {
    @Override
    @SuppressWarnings("unchecked")
    public void processTuple(Map<String, String> tuple)
    {
      store.putAll((Map<Object, Object>)(Map<?, ?>)tuple);
    }

  }

  public void testInputOperator() throws Exception
  {
    store.connect();
    store.put("test_abc", "789");
    store.put("test_def", "456");
    store.put("test_ghi", "123");
    store.disconnect();

    try {
      LocalMode lma = LocalMode.newInstance();
      DAG dag = lma.getDAG();
      @SuppressWarnings("unchecked")
      InputOperator<S> inputOperator = dag.addOperator("input", new InputOperator<S>());
      CollectorModule<Object> collector = dag.addOperator("collector", new CollectorModule<Object>());
      inputOperator.addKey("test_abc");
      inputOperator.addKey("test_def");
      inputOperator.addKey("test_ghi");
      inputOperator.setStore(store);
      dag.addStream("stream", inputOperator.outputPort, collector.inputPort);
      final LocalMode.Controller lc = lma.getController();
      lc.run(3000);
      lc.shutdown();
      Assert.assertEquals(CollectorModule.resultMap.get("test_abc"), "789");
      Assert.assertEquals(CollectorModule.resultMap.get("test_def"), "456");
      Assert.assertEquals(CollectorModule.resultMap.get("test_ghi"), "123");

    }
    finally {
      store.connect();
      store.remove("test_abc");
      store.remove("test_def");
      store.remove("test_ghi");
      store.disconnect();
    }
  }

  public void testOutputOperator() throws IOException
  {
    OutputOperator<S> outputOperator = new OutputOperator<S>();
    try {
      outputOperator.setStore(store);
      outputOperator.setup(null);
      outputOperator.beginWindow(100);
      Map<String, String> m = new HashMap<String, String>();
      m.put("test_abc", "123");
      m.put("test_def", "456");
      outputOperator.input.process(m);
      m = new HashMap<String, String>();
      m.put("test_ghi", "789");
      outputOperator.input.process(m);
      outputOperator.endWindow();
      outputOperator.teardown();
      store.connect();
      Assert.assertEquals(store.get("test_abc"), "123");
      Assert.assertEquals(store.get("test_def"), "456");
      Assert.assertEquals(store.get("test_ghi"), "789");
    }
    finally {
      store.remove("test_abc");
      store.remove("test_def");
      store.remove("test_ghi");
      store.disconnect();
    }
  }

}