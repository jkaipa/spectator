/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helpers for creating bucketing functions.
 */
public final class BucketFunctions {

  private static final List<ValueFormatter> FORMATTERS = new ArrayList<>();

  static {
    FORMATTERS.add(fmt(TimeUnit.NANOSECONDS.toNanos(10),     1, "ns",  TimeUnit.NANOSECONDS));
    FORMATTERS.add(fmt(TimeUnit.NANOSECONDS.toNanos(100),    2, "ns",  TimeUnit.NANOSECONDS));
    FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(1),     3, "ns",  TimeUnit.NANOSECONDS));
    FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(10),    1, "us",  TimeUnit.MICROSECONDS));
    FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(100),   2, "us",  TimeUnit.MICROSECONDS));
    FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(1),     3, "us",  TimeUnit.MICROSECONDS));
    FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(10),    1, "ms",  TimeUnit.MILLISECONDS));
    FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(100),   2, "ms",  TimeUnit.MILLISECONDS));
    FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(1),          3, "ms",  TimeUnit.MILLISECONDS));
    FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(10),         1, "s",   TimeUnit.SECONDS));
    FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(100),        2, "s",   TimeUnit.SECONDS));
    FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(8),          3, "s",   TimeUnit.SECONDS));
    FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(10),         1, "min", TimeUnit.MINUTES));
    FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(100),        2, "min", TimeUnit.MINUTES));
    FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(8),            3, "min", TimeUnit.MINUTES));
    FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(10),           1, "h",   TimeUnit.HOURS));
    FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(100),          2, "h",   TimeUnit.HOURS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(8),             1, "h",   TimeUnit.HOURS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(10),            1, "d",   TimeUnit.DAYS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(100),           2, "d",   TimeUnit.DAYS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(1000),          3, "d",   TimeUnit.DAYS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(10000),         4, "d",   TimeUnit.DAYS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(100000),        5, "d",   TimeUnit.DAYS));
    FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(1000000),       6, "d",   TimeUnit.DAYS));
    // TimeUnit.NANOSECONDS.toDays(java.lang.Long.MAX_VALUE) == 106751
  }

  private static ValueFormatter fmt(long max, int width, String suffix, TimeUnit unit) {
    return new ValueFormatter(max, width, suffix, unit);
  }

  private BucketFunctions() {
  }

  private static ValueFormatter getFormatter(long max) {
    for (ValueFormatter f : FORMATTERS) {
      if (max < f.max) {
        return f;
      }
    }
    return new ValueFormatter(max, ("" + max).length(), "ns", TimeUnit.NANOSECONDS);
  }

  private static BucketFunction timeBiasZero(String ltZero, String gtMax, long max, TimeUnit unit) {
    final long nanos = unit.toNanos(max);
    final ValueFormatter f = getFormatter(nanos);
    List<Bucket> buckets = new ArrayList<>();
    buckets.add(new Bucket(ltZero, 0L));
    buckets.add(new Bucket(f.apply(nanos / 8), nanos / 8));
    buckets.add(new Bucket(f.apply(nanos / 4), nanos / 4));
    buckets.add(new Bucket(f.apply(nanos / 2), nanos / 2));
    buckets.add(new Bucket(f.apply(nanos), nanos));
    return new ListBucketFunction(buckets, gtMax);
  }

  private static BucketFunction timeBiasMax(String ltZero, String gtMax, long max, TimeUnit unit) {
    final long nanos = unit.toNanos(max);
    final ValueFormatter f = getFormatter(nanos);
    List<Bucket> buckets = new ArrayList<>();
    buckets.add(new Bucket(ltZero, 0L));
    buckets.add(new Bucket(f.apply(nanos - nanos / 2), nanos - nanos / 2));
    buckets.add(new Bucket(f.apply(nanos - nanos / 4), nanos - nanos / 4));
    buckets.add(new Bucket(f.apply(nanos - nanos / 8), nanos - nanos / 8));
    buckets.add(new Bucket(f.apply(nanos), nanos));
    return new ListBucketFunction(buckets, gtMax);
  }

  /**
   * Returns a function that maps age values to a set of buckets. Example use-case would be
   * tracking the age of data flowing through a processing pipeline. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max / 2, max / 4,
   * and max / 8.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static BucketFunction age(long max, TimeUnit unit) {
    return timeBiasZero("future", "old", max, unit);
  }

  /**
   * Returns a function that maps latencies to a set of buckets. Example use-case would be
   * tracking the amount of time to process a request on a server. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max / 2,
   * max / 4, and max / 8.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static BucketFunction latency(long max, TimeUnit unit) {
    return timeBiasZero("negative_latency", "slow", max, unit);
  }

  /**
   * Returns a function that maps age values to a set of buckets. Example use-case would be
   * tracking the age of data flowing through a processing pipeline. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max - max / 8,
   * max - max / 4, and max - max / 2.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static BucketFunction ageBiasOld(long max, TimeUnit unit) {
    return timeBiasMax("future", "old", max, unit);
  }

  /**
   * Returns a function that maps latencies to a set of buckets. Example use-case would be
   * tracking the amount of time to process a request on a server. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max - max / 8,
   * max - max / 4, and max - max / 2.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static BucketFunction latencyBiasSlow(long max, TimeUnit unit) {
    return timeBiasMax("negative_latency", "slow", max, unit);
  }

  private static class ValueFormatter {
    private final long max;
    private final String fmt;
    private final TimeUnit unit;

    ValueFormatter(long max, int width, String suffix, TimeUnit unit) {
      this.max = max;
      this.fmt = "%0" + width + "d" + suffix;
      this.unit = unit;
    }

    String apply(long v) {
      return String.format(fmt, unit.convert(v, TimeUnit.NANOSECONDS));
    }
  }

  private static class ListBucketFunction implements BucketFunction {
    private final List<Bucket> buckets;
    private final String fallback;

    ListBucketFunction(List<Bucket> buckets, String fallback) {
      this.buckets = buckets;
      this.fallback = fallback;
    }

    @Override public String apply(long amount) {
      for (Bucket b : buckets) {
        if (amount < b.upperBoundary) {
          return b.name();
        }
      }
      return fallback;
    }
  }

  private static class Bucket {
    private final String name;
    private final long upperBoundary;

    Bucket(String name, long upperBoundary) {
      this.name = name;
      this.upperBoundary = upperBoundary;
    }

    String name() {
      return name;
    }

    long upperBoundary() {
      return upperBoundary;
    }
  }
}
