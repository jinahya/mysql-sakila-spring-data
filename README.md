# mysql-sakila-spring-data

Spring Data repositories for the [MySQL Sakila Sample Database](https://dev.mysql.com/doc/sakila/en/), built on the
Jakarta Persistence entities of [mysql-sakila-persistence](https://github.com/jinahya/mysql-sakila-persistence).

One repository per upstream entity, in `com.github.jinahya.mysql.sakila.data`, each extending `_Repository<E, ID>`
(`JpaRepository` + `JpaSpecificationExecutor`). Most of them, and their tests, are still empty; `ActorRepository` is
the one with query methods so far. The views upstream maps without `@Entity` (`film_list`,
`nicer_but_slower_film_list`, `sales_by_store`) are not Spring Data repositories; `_NonEntityRepository` and
`_NonEntityRepositoryImpl` are the bases for hand-written ones over `JdbcClient`.

## Requirements

* JDK 25 to build (enforced); the published jar targets 17, as upstream does
* Maven 3.x; there is no wrapper here
* Spring Boot 4.1.x, Spring Data JPA (`provided`: bring it, and a JPA provider, yourself)

The upstream entity jar, and its `test-companion` jar of randomizers and persisters, are `0.0.1-SNAPSHOT`s that exist
only locally. Install them first:

```sh
(cd ../mysql-sakila-persistence && ./mvnw -DskipTests install)
```

## Build

```sh
mvn clean install                                   # unit tests only (embedded H2 in MySQL mode)
mvn -Dtest=ActorRepository_DataJpaTest test
```

### Integration tests

The `*_SpringBootIT` classes run against a real Sakila database and are not run by `install`/`test`. Start the
container that upstream provides, which serves `sakila` on `localhost:3306` as `root` / `password` with the sample data
loaded, then name the tests:

```sh
../mysql-sakila-persistence/_docker_compose_up.sh                                 # Ctrl-C takes it down
mvn -Dtest='*_SpringBootIT' -Dsurefire.failIfNoSpecifiedTests=false test
```

`SAKILA_PASSWORD` overrides the password. The tests only read the sample data, and never generate DDL.

## Links

### [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)

* [Defining Query Methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html)
* [Using JPA Named Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.query-methods.named-queries)
* [Streaming Query Results](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.query-streaming)
* [Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)

### [Spring Boot](https://docs.spring.io/spring-boot/reference/)

* [Auto-configured Data JPA Tests](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html#testing.spring-boot-applications.autoconfigured-spring-data-jpa)
