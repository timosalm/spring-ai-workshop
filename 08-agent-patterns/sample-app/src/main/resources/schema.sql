CREATE TABLE IF NOT EXISTS support_ticket (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      summary VARCHAR(255) NOT NULL,
      category VARCHAR(50) NOT NULL,
      priority VARCHAR(20) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP NOT NULL);
