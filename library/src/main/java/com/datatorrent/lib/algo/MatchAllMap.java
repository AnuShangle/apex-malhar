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
package com.datatorrent.lib.algo;

import java.util.Map;

import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.annotation.InputPortFieldAnnotation;
import com.datatorrent.api.annotation.OutputPortFieldAnnotation;
import com.datatorrent.lib.util.BaseMatchOperator;
import com.datatorrent.lib.util.UnifierBooleanAnd;

/**
 *
 * Each tuple is tested for the compare function. The function is given by
 * "key", "value", and "cmp". If all tuples passes a Boolean(true) is emitted, else a Boolean(false) is emitted on end of window on the output port "all".
 * The comparison is done by getting double value from the Number.<p>
 * This module is an end of window module<br>
 * <br>
 * <b>StateFull : Yes, </b> tuple are compared across application window(s). <br>
 * <b>Partitions : Yes, </b> match status is unified on output port. <br>
 * <br>
 * <b>Ports</b>:<br>
 * <b>data</b>: expects Map&lt;K,V extends Number&gt;<br>
 * <b>all</b>: emits Boolean<br>
 * <br>
 * <b>Properties</b>:<br>
 * <b>key</b>: The key on which compare is done<br>
 * <b>value</b>: The value to compare with<br>
 * <b>cmp</b>: The compare function. Supported values are "lte", "lt", "eq", "neq", "gt", "gte". Default is "eq"<br>
 * <br>
 * <b>Specific compile time checks</b>:<br>
 * Key must be non empty<br>
 * Value must be able to convert to a "double"<br>
 * Compare string, if specified, must be one of "lte", "lt", "eq", "neq", "gt", "gte"<br>
 * <br>
 *
 * @since 0.3.2
 */
public class MatchAllMap<K, V extends Number> extends BaseMatchOperator<K, V>
{
  @InputPortFieldAnnotation(name = "data")
  public final transient DefaultInputPort<Map<K, V>> data = new DefaultInputPort<Map<K, V>>()
  {
    /**
     * Sets match flag to false for on first non matching tuple
     */
    @Override
    public void process(Map<K, V> tuple)
    {
      if (!result) {
        return;
      }
      V val = tuple.get(getKey());
      if (val == null) { // skip if key does not exist
        return;
      }
      result = compareValue(val.doubleValue());
    }
  };
  @OutputPortFieldAnnotation(name = "all")
  public final transient DefaultOutputPort<Boolean> all = new DefaultOutputPort<Boolean>()
  {
    @Override
    public Unifier<Boolean> getUnifier()
    {
      return new UnifierBooleanAnd();
    }
  };

  protected boolean result = true;


  /**
   * Emits the match flag
   */
  @Override
  public void endWindow()
  {
    all.emit(result);
    result = true;
  }
}
