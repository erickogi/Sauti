export const CLAIM_SLOT = `local op = 'claim'
local exists = redis.call('HEXISTS', KEYS[1], ARGV[1])
if exists == 1 then return 'exists' end
local count = redis.call('HLEN', KEYS[1])
if count >= tonumber(ARGV[2]) then return 'full' end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
return 'ok'`;

export const RECLAIM_SLOT = `local op = 'reclaim'
local cur = redis.call('HGET', KEYS[1], ARGV[1])
if not cur then return 'gone' end
local sep = string.find(cur, '\\1', 1, true)
local gen = string.sub(cur, 1, sep - 1)
if gen ~= ARGV[2] then return 'mismatch' end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
return 'ok'`;

export const SWEEP_SLOT = `local op = 'sweep'
local cur = redis.call('HGET', KEYS[1], ARGV[1])
if not cur then return 'gone' end
local s1 = string.find(cur, '\\1', 1, true)
local s2 = string.find(cur, '\\1', s1 + 1, true)
local s3 = string.find(cur, '\\1', s2 + 1, true)
local gen = string.sub(cur, 1, s1 - 1)
local conn = string.sub(cur, s1 + 1, s2 - 1)
local epoch = string.sub(cur, s2 + 1, s3 - 1)
if gen ~= ARGV[2] then return 'kept' end
if epoch ~= ARGV[3] then return 'kept' end
if conn ~= 'unreachable' then return 'kept' end
redis.call('HDEL', KEYS[1], ARGV[1])
return 'swept'`;

export const UPDATE_SLOT = `local op = 'update'
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
return 'ok'`;
