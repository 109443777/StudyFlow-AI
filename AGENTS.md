# StudyFlow AI repo rules

- Backend: Spring Boot 3, Java 17, Maven
- ORM: MyBatis-Plus
- DB: MySQL
- Cache: Redis
- MQ: RabbitMQ
- Object storage: MinIO
- AI integration: LangChain4j
- Code layers: controller / service / mapper / entity / dto / vo
- All APIs return Result<T>
- Every table has create_time and update_time
- New feature must include SQL, DTO, VO, controller, service, mapper
- Any code change must pass mvn test
- AI calls must be wrapped behind gateway interfaces