package com.zdawn.jwt.spi.impl;

import java.util.List;
import java.util.UUID;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdawn.jwt.spi.Token;

public class TestRedisTokenStore {
	public static Token createToken(String uid, String type) {
		Token token  = new Token();
		token.setTokenId(UUID.randomUUID().toString());
		token.setUserId(uid);
		long currentTime = System.currentTimeMillis();
		token.setCreateTime(currentTime);
		token.setLastUseTime(currentTime);
		token.setTokenState(0);
		int tokenType = 2;//timekeeping
		if("metering".equals(type)) tokenType = 1;
		token.setTokenType(tokenType);
		token.setUseNumber(1);
		return token;
	}
	
	public static void main(String[] args) {
		try {
			Config config = new Config();
			config.useSingleServer().setAddress("localhost:6379");
			RedissonClient redisson = Redisson.create(config);
			RedisTokenStore redis = new RedisTokenStore();
			redis.setRedissonClient(redisson);
			//save
			Token tokenOne = TestRedisTokenStore.createToken("admin", "timekeeping");
			redis.saveToken(tokenOne);
			//get all
			List<Token> list = redis.queryTokenByUserId("admin");
			ObjectMapper objectMapper = new ObjectMapper();
			System.out.println("---------get all-----------------");
			System.out.println(objectMapper.writeValueAsString(list));
			//get one
			System.out.println("---------get--------------------");
			System.out.println(objectMapper.writeValueAsString(redis.queryTokenById(tokenOne.getTokenId())));
			//update
			Thread.sleep(10000);
			tokenOne.setLastUseTime(System.currentTimeMillis());
			redis.updateToken(tokenOne);
			System.out.println("---------update--------------------");
			//get all
			list = redis.queryTokenByUserId("admin");
			System.out.println("---------get all-----------------");
			System.out.println(objectMapper.writeValueAsString(list));
			//删除
			redis.delTokenById(tokenOne.getTokenId());
			System.out.println("---------delete-----------------");
			//get
			System.out.println("---------get-----------------");
			System.out.println(objectMapper.writeValueAsString(redis.queryTokenById(tokenOne.getTokenId())));
			//get all
			list = redis.queryTokenByUserId("admin");
			System.out.println("---------get all-----------------");
			System.out.println(objectMapper.writeValueAsString(list));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
