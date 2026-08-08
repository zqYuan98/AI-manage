package com.ailab.system.report;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Token-owned Redis lock; compare-and-delete prevents releasing a later worker's lease. */
@Component
public final class RedisReportJobLock implements ReportJobLock {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<Long>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<Long>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('pexpire',KEYS[1],180000) else return 0 end", Long.class);
    private final RedisTemplate<Object,Object> redis;
    public RedisReportJobLock(RedisTemplate<Object,Object> redis) { this.redis = redis; }
    @Override public String tryAcquire(Long reportId, String step) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key(reportId, step), token, 180, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }
    @Override public boolean renew(Long reportId, String step, String token) {
        Long result=redis.execute(RENEW,Collections.<Object>singletonList(key(reportId,step)),token);
        return Long.valueOf(1L).equals(result);
    }
    @Override public void release(Long reportId, String step, String token) {
        if (token != null) redis.execute(RELEASE, Collections.<Object>singletonList(key(reportId, step)), token);
    }
    private String key(Long reportId, String step) { return "ailab:report:job:" + reportId + ":" + step; }
}
