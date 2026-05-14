package com.goke.videotest.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AverageTime {
    private static final int N = 128;
    public static boolean DEBUG = false;

    private Map<Long, Long> mTimeStamp = new ConcurrentHashMap<>(2 * N);
    private List<Long> mTime = new ArrayList<>(N);

    private static Map<String, AverageTime> sInstance = new HashMap<String, AverageTime>();

    public synchronized static AverageTime getInstance(String key) {
        if (!sInstance.containsKey(key)) {
            sInstance.put(key, new AverageTime());
        }
        return sInstance.get(key);
    }

    public void push(long u) {
        if (!DEBUG) return;
        long presentationTimeUs = System.nanoTime() / 1000;
        if (mTimeStamp.size() >= N) mTimeStamp.clear();
        mTimeStamp.put(u, presentationTimeUs);
    }

    public void pop(long u) {
        if (!DEBUG) return;
        if (mTimeStamp.containsKey(u)) {
            long presentationTimeUs = System.nanoTime() / 1000;
            mTime.add(presentationTimeUs - mTimeStamp.get(u));
            mTimeStamp.remove(u);
        }
    }

    public long averageUs() {
        if (!DEBUG) return 0;
        long e = mTime.stream().collect(Collectors.averagingLong(Long::longValue)).longValue();
        mTime.clear();
        return e;
    }

    public long maximumUs() {
        if (!DEBUG) return 0;
        long e = mTime.stream().mapToLong(Long::longValue).max().orElse(0);
        mTime.clear();
        return e;
    }

    public String print() {
        if (!DEBUG) return "";
        LongSummaryStatistics stat = mTime.stream().mapToLong(Long::longValue).summaryStatistics();
        StringBuilder sb = new StringBuilder();
        if (stat.getCount() > 0) {
            sb.append((long) stat.getAverage() / 1000 + ", ");
            sb.append("max: " + stat.getMax() / 1000 + ", ");
            sb.append("cnt: " + stat.getCount() + " " + mTime);
        }
        mTime.clear();
        return sb.toString();
    }
}
