package com.gupaoedu.vip.spring.demo.service.impl;


import com.gupaoedu.vip.spring.demo.service.IDemoService;
import com.gupaoedu.vip.spring.framework.annotation.GPService;

/**
 * 核心业务逻辑
 */
@GPService
public class DemoService implements IDemoService {

	@Override
	public String get(String name) {
		return "My name is " + name + ",from service.";
	}

}
