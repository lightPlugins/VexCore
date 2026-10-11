if redis.call('GET', KEYS[2]) ~= ARGV[1] then return redis.error_reply('LIVE_EPOCH_CHANGED') end
if redis.call('HEXISTS', KEYS[1], '__schema') ~= 1 then return redis.error_reply('LIVE_STATE_MISSING') end
local fields = cjson.decode(ARGV[2])
local result = {}
for _, key in ipairs(fields) do
    local value = redis.call('HGET', KEYS[1], key)
    if value then result[key] = value end
end
return cjson.encode(result)
