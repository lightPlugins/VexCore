-- All authority keys use one namespace. Validate before writing: Redis script errors do not roll back writes.
if redis.call('GET', KEYS[2]) ~= ARGV[1] then return redis.error_reply('LIVE_EPOCH_CHANGED') end
if redis.call('HEXISTS', KEYS[1], '__schema') ~= 1 then return redis.error_reply('LIVE_STATE_MISSING') end
local previous = redis.call('HGET', KEYS[4], ARGV[2])
if previous then return previous end
local journalLength = redis.call('XLEN', KEYS[3])
local position = redis.call('HGET', KEYS[1], '__position') or '0-0'
if journalLength == 0 and position ~= '0-0' then return redis.error_reply('LIVE_JOURNAL_MISSING') end
if journalLength >= tonumber(ARGV[4]) then return redis.error_reply('CHECKPOINT_BACKLOG_FULL') end
local changes = cjson.decode(ARGV[3])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now - tonumber(ARGV[5]) > 60000 then return redis.error_reply('OPERATION_EXPIRED') end
local writes = {}
for _, change in ipairs(changes) do
    local raw = redis.call('HGET', KEYS[1], change.key)
    local revision = raw and cjson.decode(raw).revision or 0
    if change.expected >= 0 and revision ~= change.expected then return 'CONFLICT' end
    if change.deadline > 0 and now >= change.deadline then return 'CONFLICT' end
    if change.value ~= cjson.null then
        writes[change.key] = cjson.encode({value = change.value, revision = revision + 1})
    elseif change.remove then
        writes[change.key] = false
    end
end
-- Check the stream and receipt types before the first state mutation.
local receiptType = redis.call('TYPE', KEYS[4]).ok
if receiptType ~= 'none' and receiptType ~= 'hash' then return redis.error_reply('INVALID_RECEIPT_TYPE') end
local ageType = redis.call('TYPE', KEYS[5]).ok
if ageType ~= 'none' and ageType ~= 'zset' then return redis.error_reply('INVALID_RECEIPT_AGE_TYPE') end
local encoded = cjson.encode(writes)
-- ACL failures must also happen before journaling: a restricted user must not leave a committed-looking delta.
if not redis.acl_check_cmd('XADD', KEYS[3], '*', 'changes', encoded, 'operation', ARGV[2])
    or not redis.acl_check_cmd('HSET', KEYS[1], '__position', 'position')
    or not redis.acl_check_cmd('HSET', KEYS[4], ARGV[2], 'receipt')
    or not redis.acl_check_cmd('ZADD', KEYS[5], now, ARGV[2]) then
    return redis.error_reply('WRITE_PERMISSION_DENIED')
end
for key, value in pairs(writes) do
    local permitted
    if value == false then permitted = redis.acl_check_cmd('HDEL', KEYS[1], key)
    else permitted = redis.acl_check_cmd('HSET', KEYS[1], key, value) end
    if not permitted then return redis.error_reply('WRITE_PERMISSION_DENIED') end
end
position = redis.call('XADD', KEYS[3], '*', 'changes', encoded, 'operation', ARGV[2])
for key, value in pairs(writes) do
    if value == false then redis.call('HDEL', KEYS[1], key) else redis.call('HSET', KEYS[1], key, value) end
end
redis.call('HSET', KEYS[1], '__position', position)
redis.call('HSET', KEYS[4], ARGV[2], position)
redis.call('ZADD', KEYS[5], now, ARGV[2])
return position
