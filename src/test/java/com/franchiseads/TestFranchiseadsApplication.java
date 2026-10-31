package com.franchiseads;

import org.springframework.boot.SpringApplication;

public class TestFranchiseadsApplication {

	public static void main(String[] args) {
		SpringApplication.from(FranchiseadsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
