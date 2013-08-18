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
package com.datatorrent.lib.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tuples are ordered by key, and bottom N of the ordered tuples per key are emitted at the end of window<p>
 * This is an end of window module<br>
 * At the end of window all data is flushed. Thus the data set is windowed and no history is kept of previous windows<br>
 * <br>
 * <b>Ports</b>
 * <b>data</b>: Input data port expects HashMap<StriK,V> (<key, value><br>
 * <b>bottom</b>: Output data port, emits HashMap<K, ArrayList<V>> (<key, ArraList<values>>)<br>
 * <b>Properties</b>:
 * <b>N</b>: The number of top values to be emitted per key<br>
 * <br>
 * <b>Benchmarks></b>: TBD<br>
 * Compile time checks are:<br>
 * N: Has to be an integer<br>
 * <br>
 * Run time checks are:<br>
 * <br>
 *
 * @since 0.3.2
 */
public abstract class AbstractBaseNNonUniqueOperatorMap<K, V> extends AbstractBaseNOperatorMap<K, V>
{
  /**
   * Override to decide the direction (ascending vs descending)
   * @return true if ascending, to be done by sub-class
   */
  abstract public boolean isAscending();

  /**
   * Override to decide which port to emit to and its schema
   * @param tuple
   */
  abstract public void emit(HashMap<K, ArrayList<V>> tuple);

  /**
   *
   * Inserts tuples into the queue
   * @param tuple to insert in the queue
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public void processTuple(Map<K, V> tuple)
  {
    for (Map.Entry<K, V> e: tuple.entrySet()) {
      TopNSort pqueue = kmap.get(e.getKey());
      if (pqueue == null) {
        pqueue = new TopNSort<V>(5, n, isAscending());
        kmap.put(cloneKey(e.getKey()), pqueue);
        pqueue.offer(cloneValue(e.getValue()));
      }
      else {
        pqueue.offer(e.getValue());
      }
    }
  }

  protected HashMap<K, TopNSort<V>> kmap = new HashMap<K, TopNSort<V>>();

  /**
   * Emits the result
   * Clears the internal data
   */
  @SuppressWarnings("unchecked")
  @Override
  public void endWindow()
  {
    for (Map.Entry<K, TopNSort<V>> e: kmap.entrySet()) {
      HashMap<K, ArrayList<V>> tuple = new HashMap<K, ArrayList<V>>(1);
      tuple.put(e.getKey(), e.getValue().getTopN(getN()));
      emit(tuple);
    }
    kmap.clear();
  }
}
