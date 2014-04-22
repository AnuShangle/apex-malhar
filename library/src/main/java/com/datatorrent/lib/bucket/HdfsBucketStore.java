/*
 * Copyright (c) 2014 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.bucket;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import javax.annotation.Nonnull;
import javax.validation.constraints.Min;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * {@link BucketStore} which works with HDFS.<br/>
 * The path of buckets in hdfs is <code>{application-path}/buckets/{operatorId}/{windowId}</code>.
 *
 * @param <T> type of bucket event
 * @since 0.9.4
 */
public class HdfsBucketStore<T extends Bucketable> implements BucketStore<T>
{
  public static transient String OPERATOR_ID = "operatorId";
  public static transient String STORE_ROOT = "storeRoot";
  public static transient String PARTITION_KEYS = "partitionKeys";
  public static transient String PARTITION_MASK = "partitionMask";

  static transient final String PATH_SEPARATOR = "/";

  //Check-pointed
  private boolean writeEventKeysOnly;
  @Min(1)
  protected int noOfBuckets;
  protected Map<Long, Long>[] bucketPositions;
  protected Map<Long, Long> windowToTimestamp;
  protected Class<?> eventKeyClass;
  protected Class<T> eventClass;

  //Non check-pointed
  protected transient Multimap<Long, Integer> windowToBuckets;
  protected transient String bucketRoot;
  protected transient Configuration configuration;
  protected transient Kryo serde;
  protected transient Set<Integer> partitionKeys;
  protected transient int partitionMask;
  protected transient int operatorId;

  public HdfsBucketStore()
  {
    windowToTimestamp = Maps.newHashMap();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setNoOfBuckets(int noOfBuckets)
  {
    this.noOfBuckets = noOfBuckets;
    bucketPositions = (Map<Long, Long>[]) Array.newInstance(HashMap.class, noOfBuckets);
  }

  @Override
  public void setWriteEventKeysOnly(boolean writeEventKeysOnly)
  {
    this.writeEventKeysOnly = writeEventKeysOnly;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  public void setup(Context context)
  {
    operatorId = Preconditions.checkNotNull(context.getInt(OPERATOR_ID, null));
    String rootPath = context.getString(STORE_ROOT, null);
    this.bucketRoot = (rootPath == null ? "buckets" : rootPath) + PATH_SEPARATOR + operatorId;
    this.partitionKeys = (Set<Integer>) Preconditions.checkNotNull(context.getObject(PARTITION_KEYS, null), "partition keys");
    this.partitionMask = Preconditions.checkNotNull(context.getInt(PARTITION_MASK, null), "partition mask");
    logger.debug("operator parameters {}, {}, {}", operatorId, partitionKeys, partitionMask);

    this.configuration = new Configuration();
    this.serde = new Kryo();
    this.serde.setClassLoader(Thread.currentThread().getContextClassLoader());
    if (logger.isDebugEnabled()) {
      for (int i = 0; i < bucketPositions.length; i++) {
        if (bucketPositions[i] != null) {
          logger.debug("bucket idx {} position {}", i, bucketPositions[i]);
        }
      }
    }
    windowToBuckets = ArrayListMultimap.create();
    for (int i = 0; i < bucketPositions.length; i++) {
      if (bucketPositions[i] != null) {
        for (Long window : bucketPositions[i].keySet()) {
          windowToBuckets.put(window, i);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void teardown()
  {
    //Not closing the filesystem.
    configuration.clear();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void storeBucketData(long window, long timestamp, Map<Integer, Map<Object, T>> data) throws IOException
  {
    Path dataFilePath = new Path(bucketRoot + PATH_SEPARATOR + window);
    FileSystem fs = FileSystem.newInstance(dataFilePath.toUri(), configuration);
    FSDataOutputStream dataStream = fs.create(dataFilePath);

    Output output = new Output(dataStream);
    try {
      long offset = 0;
      for (int bucketIdx : data.keySet()) {
        Map<Object, T> bucketData = data.get(bucketIdx);

        if (eventKeyClass == null) {
          Map.Entry<Object, T> eventEntry = bucketData.entrySet().iterator().next();
          eventKeyClass = eventEntry.getKey().getClass();
          if (!writeEventKeysOnly) {
            @SuppressWarnings("unchecked")
            Class<T> lEventClass = (Class<T>) eventEntry.getValue().getClass();
            eventClass = lEventClass;
          }
        }
        //Write the size of data and then data
        dataStream.writeInt(bucketData.size());
        for (Map.Entry<Object, T> entry : bucketData.entrySet()) {
          serde.writeObject(output, entry.getKey());

          if (!writeEventKeysOnly) {
            int posLength = output.position();
            output.writeInt(0); //temporary place holder
            serde.writeObject(output, entry.getValue());
            int posValue = output.position();
            int valueLength = posValue - posLength - 4;
            output.setPosition(posLength);
            output.writeInt(valueLength);
            output.setPosition(posValue);
          }
        }
        output.flush();
        if (bucketPositions[bucketIdx] == null) {
          bucketPositions[bucketIdx] = Maps.newHashMap();
        }
        windowToBuckets.put(window, bucketIdx);
        windowToTimestamp.put(window, timestamp);
        synchronized (bucketPositions[bucketIdx]) {
          bucketPositions[bucketIdx].put(window, offset);
        }
        offset = dataStream.getPos();
      }
    }
    finally {
      output.close();
      dataStream.close();
      fs.close();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteBucket(int bucketIdx) throws IOException
  {
    Map<Long, Long> offsetMap = bucketPositions[bucketIdx];
    if (offsetMap != null) {
      for (Long window : offsetMap.keySet()) {
        Collection<Integer> indices = windowToBuckets.get(window);
        synchronized (indices) {
          boolean elementRemoved = indices.remove(bucketIdx);
          if (indices.isEmpty() && elementRemoved) {
            Path dataFilePath = new Path(bucketRoot + PATH_SEPARATOR + window);
            FileSystem fs = FileSystem.newInstance(dataFilePath.toUri(), configuration);
            try {
              if (fs.exists(dataFilePath)) {
                fs.delete(dataFilePath, true);
                logger.debug("{} deleted file {}", operatorId, window);
              }
              windowToBuckets.removeAll(window);
              windowToTimestamp.remove(window);
            }
            finally {
              fs.close();
            }
          }
        }
      }
    }
    bucketPositions[bucketIdx] = null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nonnull
  public Map<Object, T> fetchBucket(int bucketIdx) throws Exception
  {
    Map<Object, T> bucketData = Maps.newHashMap();

    if (bucketPositions[bucketIdx] == null) {
      return bucketData;
    }

    logger.debug("fetching bucket {}", bucketIdx);
    for (long window : bucketPositions[bucketIdx].keySet()) {

      //Read data only for the fileIds in which bucketIdx had events.
      Path dataFile = new Path(bucketRoot + PATH_SEPARATOR + window);
      FileSystem fs = FileSystem.newInstance(dataFile.toUri(), configuration);
      FSDataInputStream stream = fs.open(dataFile);
      stream.seek(bucketPositions[bucketIdx].get(window));
      Input input = new Input(stream);
      try {
        int length = stream.readInt();

        for (int i = 0; i < length; i++) {
          Object key = serde.readObject(input, eventKeyClass);

          int partitionKey = key.hashCode() & partitionMask;
          boolean keyPasses = partitionKeys.contains(partitionKey);

          if (!writeEventKeysOnly) {
            //if key passes then read the value otherwise skip the value
            int entrySize = input.readInt();
            if (keyPasses) {
              T entry = serde.readObject(input, eventClass);
              bucketData.put(key, entry);
            }
            else {
              input.skip(entrySize);
            }
          }
          else if (keyPasses) {
            bucketData.put(key, null);
          }
        }
      }
      finally {
        input.close();
        stream.close();
        fs.close();
      }
    }
    return bucketData;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HdfsBucketStore)) {
      return false;
    }

    HdfsBucketStore that = (HdfsBucketStore) o;

    if (noOfBuckets != that.noOfBuckets) {
      return false;
    }
    if (writeEventKeysOnly != that.writeEventKeysOnly) {
      return false;
    }
    return Arrays.equals(bucketPositions, that.bucketPositions);

  }

  @Override
  public int hashCode()
  {
    int result = (writeEventKeysOnly ? 1 : 0);
    result = 31 * result + noOfBuckets;
    result = 31 * result + (bucketPositions != null ? Arrays.hashCode(bucketPositions) : 0);
    return result;
  }

  private static transient final Logger logger = LoggerFactory.getLogger(HdfsBucketStore.class);
}
