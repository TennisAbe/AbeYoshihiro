package com.abeyoshihiro.character_manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:context")
class CharacterManagerApplicationTests {

	@Test
	void contextLoads() {
	}

}
