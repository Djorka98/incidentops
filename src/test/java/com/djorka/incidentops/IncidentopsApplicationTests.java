package com.djorka.incidentops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:incidentops-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
class IncidentopsApplicationTests {

	@Test
	void contextLoads() {
	}

}
