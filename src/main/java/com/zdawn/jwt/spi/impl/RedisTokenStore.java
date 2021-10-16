package com.zdawn.jwt.spi.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdawn.jwt.spi.Token;
import com.zdawn.jwt.spi.TokenStore;
import com.zdawn.jwt.spi.WebToken;
/**
 * 使用 org.redisson包 存储 token
 * @author zhaobs
 */
public class RedisTokenStore implements TokenStore {
	private static Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);
	
	private RedissonClient redissonClient;
	//token 前缀
	private String prefix = "zdawn:jwttoken:";
	//过期时间 分钟
	private int expireTime = 30;
	private int meteringCount = 1;

	@Override
	public void saveToken(Token token) throws Exception {
		try {
			RBucket<Token> rBucket = redissonClient.getBucket(prefix+token.getTokenId());
			rBucket.set(token, getTimeToLive(), TimeUnit.MINUTES);
			keepUidMap(token);
		} catch (Exception e) {
			logger.error("saveToken",e);
		}
	}

	@Override
	public Token queryTokenById(String tokenId) throws Exception {
		Token token = null;
		try {
			RBucket<Token> rBucket = redissonClient.getBucket(prefix+tokenId);
			if(rBucket.isExists()) token = rBucket.get();
		} catch (Exception e) {
			logger.error("queryTokenById",e);
		}
		return token;
	}

	@Override
	public List<Token> queryTokenByUserId(String userId) throws Exception {
		List<Token> list = null;
		try {
			RMap<Object, Object> rMap = redissonClient.getMap(prefix+userId);
			if(!rMap.isExists()) return null;
			Collection<Object> values = rMap.readAllValues();
			list = new ArrayList<>();
			List<String> keyList = new ArrayList<>();
			for (Iterator<?> it = values.iterator(); it.hasNext();) {
				Token token = (Token) it.next();
				if(expire(token)) {
					keyList.add(token.getTokenId());
				}else {
					list.add(token);
				}
			}
			//删除过期token
			if(keyList.size()>0) rMap.fastRemove(keyList);
		} catch (Exception e) {
			logger.error("queryTokenByUserId",e);
		}
		return list;
	}
	
	private boolean expire(Token token) {
		long currentTime = System.currentTimeMillis();
		if(1==token.getTokenType()) {//metering
			if(currentTime-token.getLastUseTime()>expireTime*60000) {
				return true;
			}
			if(meteringCount<=token.getUseNumber()) {
				return true;
			}
		}else {//timekeeping
			if(currentTime-token.getLastUseTime()>expireTime*60000) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void updateToken(Token token) throws Exception {
		try {
			RBucket<Token> rBucket = redissonClient.getBucket(prefix+token.getTokenId());
			rBucket.set(token, getTimeToLive(), TimeUnit.MINUTES);
			keepUidMap(token);
		} catch (Exception e) {
			logger.error("updateToken",e);
		}
	}

	@Override
	public void delTokenById(String tokenId) throws Exception {
		try {
			RBucket<Token> rBucket = redissonClient.getBucket(prefix+tokenId);
			if(rBucket.isExists()) {
				Token token = rBucket.get();
				rBucket.delete();
				delTokenFromUidMap(token);
			}
		} catch (Exception e) {
			logger.error("delTokenById",e);
		}
	}
	//维护uid列表
	private void keepUidMap(Token token) {
		try {
			RMap<Object, Object> rMap = redissonClient.getMap(prefix+token.getUserId());
			rMap.fastPut(token.getTokenId(), token);
			boolean expire = rMap.expire(getTimeToLive(), TimeUnit.MINUTES);
			if(!expire) logger.warn("map expire not success "+token.getUserId());
		} catch (Exception e) {
			logger.error("keepUidList",e);
		}
	}
	//删除uid列表中的元素
	private void delTokenFromUidMap(Token token) {
		try {
			RMap<Object, Object> rMap = redissonClient.getMap(prefix+token.getUserId());
			rMap.fastRemove(token.getTokenId());
		} catch (Exception e) {
			logger.error("delTokenFromUidMap",e);
		}
	}

	@Override
	public void clearTokenByOverTime(int expireTime) throws Exception {
	}

	@Override
	public void moveHistoryTokenByOverTime(int expireTime) throws Exception {
	}

	@Override
	public void moveHistoryToken(String tokenId) throws Exception {
	}
	
	@Override
	public void validateTokenConfig(WebToken webToken) {
		Map<String, String> config = webToken.getTokenConfig();
		String tokenHistory = config.get("tokenHistory");
		if(tokenHistory.equals("true")) throw new RuntimeException("RedisTokenStore 过期的token转存至历史");
		this.expireTime = Integer.parseInt(config.get("expireTime"));
		this.meteringCount = Integer.parseInt(config.get("meteringCount"));
	}

	private int getTimeToLive() {
		return expireTime + 1;
	}

	public void setRedissonClient(RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
	}
}
