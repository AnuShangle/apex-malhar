/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
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

package com.datatorrent.lib.appdata.dimensions;

import com.datatorrent.lib.appdata.gpo.GPOMutable;
import com.datatorrent.lib.appdata.schemas.FieldsDescriptor;
import com.datatorrent.lib.appdata.schemas.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;

import java.util.List;

public class AggregatorAverage implements DimensionsOTFAggregator, Serializable
{
  private static final long serialVersionUID = 20154301644L;

  public static int SUM_INDEX = 0;
  public static int COUNT_INDEX = 1;

  public static final transient List<Class<? extends DimensionsIncrementalAggregator>> CHILD_AGGREGATORS =
  ImmutableList.of(AggregatorStaticType.SUM.getAggregator().getClass(),
                   AggregatorStaticType.COUNT.getAggregator().getClass());

  public AggregatorAverage()
  {
  }

  @Override
  public List<Class<? extends DimensionsIncrementalAggregator>> getChildAggregators()
  {
    return CHILD_AGGREGATORS;
  }

  @Override
  public GPOMutable aggregate(FieldsDescriptor inputDescriptor,
                              FieldsDescriptor outputDescriptor,
                              GPOMutable... aggregates)
  {
    Preconditions.checkArgument(aggregates.length == getChildAggregators().size(),
                                "The number of arguments " + aggregates.length +
                                " should be the same as the number of child aggregators " + getChildAggregators().size());

    GPOMutable sumAggregation = aggregates[SUM_INDEX];
    GPOMutable countAggregation = aggregates[COUNT_INDEX];

    GPOMutable result = new GPOMutable(outputDescriptor);

    long count = countAggregation.getFieldsLong()[0];

    for(String field: inputDescriptor.getFieldList()) {
      Type type = outputDescriptor.getType(field);

      switch(type) {
        case BYTE:
        {
          float val = ((float) sumAggregation.getFieldByte(field)) /
                      ((float) count);
          result.setField(field, val);
          break;
        }
        case SHORT:
        {
          float val = ((float) sumAggregation.getFieldShort(field)) /
                      ((float) count);
          result.setField(field, val);
          break;
        }
        case INTEGER:
        {
          float val = ((float) sumAggregation.getFieldInt(field)) /
                      ((float) count);
          result.setField(field, val);
          break;
        }
        case LONG:
        {
          double val = ((double) sumAggregation.getFieldLong(field)) /
                       ((double) count);
          result.setField(field, val);
          break;
        }
        case FLOAT:
        {
          float val = sumAggregation.getFieldFloat(field) /
                      ((float) count);
          result.setField(field, val);
          break;
        }
        case DOUBLE:
        {
          double val = sumAggregation.getFieldDouble(field) /
                       ((double) count);
          result.setField(field, val);
          break;
        }
        default:
        {
          throw new UnsupportedOperationException("The type " + type + " is not supported.");
        }
      }
    }

    return result;
  }

  /*@Override
  public FieldsDescriptor getResultDescriptor(FieldsDescriptor fd)
  {
    Map<String, Type> fieldToType = Maps.newHashMap();

    Preconditions.checkArgument(TYPE_CONVERSION_MAP.getTypeMap().keySet().containsAll(
                                fd.getFieldToType().values()));

    for(Map.Entry<String, Type> entry: fd.getFieldToType().entrySet()) {
      fieldToType.put(entry.getKey(), TYPE_CONVERSION_MAP.getTypeMap().get(entry.getValue()));
    }

    return new FieldsDescriptor(fieldToType);
  }*/

  @Override
  public Type getOutputType()
  {
    return Type.DOUBLE;
  }
}
