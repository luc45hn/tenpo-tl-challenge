-- Sliding window log rate limiter, executed atomically by Redis.
--
-- KEYS[1]  sorted set with one entry per accepted request, scored by its time in microseconds
-- ARGV[1]  maximum number of requests within the window
-- ARGV[2]  window length in milliseconds
-- ARGV[3]  unique id of this request, so two requests at the same microsecond get distinct members
--
-- Returns {1, 0} when the request is accepted, or {0, retry_after_micros} when it is rejected,
-- where retry_after_micros is the time until the oldest entry leaves the window.

local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_millis = tonumber(ARGV[2])
local window_micros = window_millis * 1000

-- Redis's own clock, so that replicas with skewed clocks agree
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000000 + tonumber(time[2])

redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window_micros)

if redis.call('ZCARD', key) < max_requests then
    redis.call('ZADD', key, now, string.format('%.0f', now) .. ':' .. ARGV[3])
    redis.call('PEXPIRE', key, window_millis)
    return {1, 0}
end

local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
return {0, tonumber(oldest[2]) + window_micros - now}
