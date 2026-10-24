# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Spring Data repositories on top of the Jakarta Persistence entities of
[`mysql-sakila-persistence`](https://github.com/jinahya/mysql-sakila-persistence), which maps the
[MySQL Sakila Sample Database](https://dev.mysql.com/doc/sakila/en/). A single, parent-less jar, modelled on the
sibling `oracle-sample-schemas-spring-data` with its one-module-per-schema layout folded into one module.

- Main package: `com.github.jinahya.mysql.sakila.data` (`@NullMarked`, JSpecify: Spring Data rejects a `null`
  argument to a repository method with `IllegalArgumentException` before any query runs; a lookup that may find
  nothing returns `Optional`).
- Upstream entities: `com.github.jinahya.mysql.sakila.persistence`, in `io.github.jinahya:mysql-sakila-persistence`.
- One repository per upstream `@Entity`, named `<Entity>Repository`, extending `_Repository<E, ID>`
  (`JpaRepository` + `JpaSpecificationExecutor`, `@NoRepositoryBean`). Most are still empty; `ActorRepository` is the
  one with query methods and tests so far, and the reference for javadoc and test style.
- The published jar carries **repositories only**: `spring-data-jpa`, `jakarta.persistence-api` and
  `jakarta.validation-api` are `provided`, so a consumer brings Spring Data JPA, a JPA provider, and (to enforce the
  constraints on repository parameters) a validation provider plus `@Validated`. No Boot starter is named in main.

## Upstream

The sibling checkout `../mysql-sakila-persistence` publishes, at `0.0.1-SNAPSHOT`, the entity jar (entities and the
generated static metamodel `Actor_`, `Film_`, ... only; its `persistence.xml` is test-only, so this project supplies
its own datasource and JPA configuration) and a second jar of the same artifact classified `test-companion`, holding
the `*_Randomizer` and `*_Persister` classes compiled with its tests. Both are **SNAPSHOTs that exist only locally**:
after a change upstream, run `./mvnw -DskipTests install` there before building here.

- **Do not copy upstream's randomizers or persisters into `src/test`.** The `test-companion` jar is on the test
  classpath, and `ObjectRandomizerUtils` / `EntityPersisterUtils` find `<Entity>_Randomizer` / `<Entity>_Persister`
  next to the entity by name, package-private or not. A copy only shadows the jar and drifts.
- **Name attributes through the static metamodel** (`root.get(Actor_.lastName)`), never a string. Where a
  compile-time constant is needed, use the entity's own `ATTRIBUTE_NAME_*` constant.
- **Named queries** are declared on the entities as `<EntityName>.<queryName>` (`Actor.selectOneByActorId`, ...); a
  repository method of exactly that name runs the query with no annotation, binding named parameters by name
  (`-parameters` is on, as upstream). None is wired to a method yet.
- **Query methods, in this order of preference.** (1) A derived query, from the method name, for a plain lookup
  (`findAllByFirstName`). (2) A `Specification` built with the Criteria API, run through
  `JpaSpecificationExecutor` (`findAll(spec, pageable)`, `count(spec)`), as a `default` method named `list...` /
  `count...`; this covers filters, `isEmpty`, correlated subqueries (`query.subquery(..)`). (3) **`@Query` only when a
  `Specification` cannot express it**: a projection grouped and aggregated (`GROUP BY`, `HAVING`, `COUNT`/`SUM`/`AVG`
  in the select); name such a method `select...`. (4) A native query (`@NativeQuery`) only for SQL that is MySQL's
  own (`MATCH ... AGAINST`, `DAYNAME`, `DATEDIFF`, `INTERVAL`); such a method runs on the Sakila database only, so it
  has no slice test.
- **Full pagination.** A query method of the kinds (2) to (4) takes a `Pageable`, the page and the sort, and returns a
  `Page<T>`; no `Limit`, no `Sort` alone, no fixed `ORDER BY` in the JPQL. The caller's `PageRequest` carries the
  order; the javadoc says which sort an exercise asks for. A grouped `@Query` declares its own `countQuery` (Spring
  Data cannot derive one from a grouped select); count the groups with `COUNT(DISTINCT ..)` or a subquery.
- **Results that are not entities are `jakarta.persistence.Tuple`** (in a `List`, `Stream`, `Page` or alone). The
  select aliases each column (`COUNT(a) AS actors`); the caller reads it by that alias with its type
  (`tuple.get("actors", Long.class)`; a native number as `Number`, so the same read holds on MySQL and H2), and the
  `Pageable` sorts by it (`Sort.by(DESC, "actors")`). The method's `@return` names the aliases and their types. The
  three views' `JdbcClient` methods keep their records, since `JdbcClient` has no `Tuple`.
- **Exercises.** Upstream's `../mysql-sakila-persistence/EXERCISES.asciidoc` (sibling root; `EXERCISES.yaml` is its registry) is the context, not
  a contract: for each repository, one or two exercises of each level (Basic, Intermediate, Advanced) become one
  method each, on the repository of the exercise's section, the exercise id (`SAKILA_ACTOR_I_01`) named in the
  method's javadoc together with the sort the exercise asks for, and one `@Nested` test class per method in both test
  classes. `ActorRepository` and its two tests are the reference. The `SCHEMA` section is not answered.
- **Upstream getters.** Single-valued association getters are public; every collection getter (`getFilmActors()`,
  `getRentals()`, ...) is package-private. A test that needs the many side reads it through that side's repository
  with a `Specification` (`b.equal(r.get(FilmActor_.actor), actor)`).
- **No metamodel attribute in a static initializer.** `Actor_.actorId` is `null` until Hibernate builds the
  metamodel, after the test class is loaded; a `static final Sort` uses the name constant,
  `Sort.by(ASC, Actor_.ACTOR_ID)`. `JpaSort.of(ASC, Actor_.actorId)` is fine inside a test method.
- Entities have `protected` constructors, `equals` by id only once assigned, and a database-generated `IDENTITY` id.
- Read `../mysql-sakila-persistence/CLAUDE.md` before touching anything about the mappings.

## Build

There is no wrapper here; use the Maven on `PATH`. The build JDK must be **25** (enforced; `.java-version` says so):
main sources compile at release 17, as upstream, tests at 25.

```sh
mvn test                                        # unit tests only (@DataJpaTest slices on H2)
mvn -Dtest=ActorRepository_DataJpaTest test
mvn -Dtest=SomeTest#method test
mvn -Dtest='*_SpringBootIT' -Dsurefire.failIfNoSpecifiedTests=false test   # integration tests; needs MySQL, see below
mvn -o dependency:tree
mvn -o enforcer:enforce -Denforcer.rules=dependencyConvergence
```

Surefire's default includes do not match `*IT`, and no failsafe plugin is configured, so `test`/`install` never run
the `*_SpringBootIT` classes; run them explicitly with `-Dtest`.

**The integration tests need the Sakila container** that upstream owns: run
`../mysql-sakila-persistence/_docker_compose_up.sh` (Ctrl-C takes it down again, with its data). It runs `mysql:9.7` as
the container `mysql-sakila` on `localhost:3306`, database `sakila`, user `root`, password `password`
(`SAKILA_PASSWORD` overrides it), loaded from the init scripts. `src/test/resources/application.yaml` connects there
with `ddl-auto: none` and `MySQLDialect` named outright. Never let a test generate DDL against it. A `@SpringBootTest`
that runs no query still passes with no database up, since the dialect is named; only a query proves the connection.

## Load-bearing pom decisions

Each is commented in `pom.xml`:

- **Spring Boot 4.x (`4.1.1`), not 3.x.** Boot 4 manages `jakarta.persistence-api` 3.2, which the upstream mappings are
  written against. Do not downgrade to dodge a problem.
- **`spring-boot-dependencies` is imported, not inherited** (the pom is parent-less). Every Spring / Boot / Hibernate
  version comes from the BOM; declare those with no `<version>`. Direct `dependencyManagement` entries beat the BOM,
  which is how the pins work: Lombok `1.18.48` (the BOM's breaks on newer JDKs), and `mysql-connector-j` and `h2` at
  upstream's versions, so the tests hit the same driver and the same H2 upstream generates its schema into.
- **Boot 4 starters come in pairs.** Tests declare `spring-boot-starter-data-jpa-test`, `-jackson-test` and
  `-validation-test`, not `spring-boot-starter-test`, which each of them brings. `@DataJpaTest` is
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. Validation of repository parameters is enforced
  in tests only, through the validation starter.
- **MyBatis is test scope only** (`mybatis-spring`, the Boot starter and its `-test`), for native SQL against the
  non-entity views; nothing in main uses it yet.
- **Dependency convergence is a build defect**, not a warning. Resolve `jakarta.*` at the platform level instead of
  pinning individual API artifacts.
- **Lombok is `provided`** for main and test, on the processor path of both compile executions.

## View repositories

Spring Data JPA builds repositories for managed types only: a repository of a non-`@Entity` class fails at startup with
`Not a managed type`. Upstream maps three views without `@Entity` (`film_list`, `nicer_but_slower_film_list`,
`sales_by_store`), because nothing identifies a row of them. For those, this project has the bases but no
implementation yet:

- `_NonEntityRepository<T>`: a plain interface, extending nothing. A subinterface declares the view's methods. No
  `findById`, `save` or `delete`: the view has no usable key and is read-only. No Spring Data repository may extend it.
- `_NonEntityRepositoryImpl<T>`: the base of the `public`, `@Repository` class `<Interface>Impl`, over an autowired
  `JdbcClient` (`jdbcClient()`), with a `@PersistenceContext` `EntityManager` (`entityManager()`) for the rare native
  query. `sql(Class, String)` reads a statement from a `.sql` resource beside the subclass, named
  `<VIEW>_<QUERY>.sql` (`FILM_LIST_COUNT_ALL.sql`), from a `static final` initializer, so a missing resource fails the
  context, not the first call. `requirePage(offset, limit)` checks page arguments. A row maps into `T` through its
  single public constructor, by parameter name.
- **Nothing registers an `Impl` automatically.** The test context must `@Import` each one; a consumer component-scans
  the package or `@Import`s the class. `@Repository` gives it exception translation (an `IllegalArgumentException`
  reaches the caller as `InvalidDataAccessApiUsageException`).
- Inside a transaction `JdbcClient` shares JPA's connection but sees only what JPA has flushed.

## Tests

The test root `com.github.jinahya.mysql.sakila.data` has `___Spring_TestContext`: `@SpringBootConfiguration` +
`@EnableAutoConfiguration` + `@EntityScan(basePackageClasses = _NoOp.class)` (upstream's marker; the entities are
outside this package). It lives in test sources on purpose: nothing in `src/main` carries a `@SpringBootApplication`.
Both `@SpringBootTest` and `@DataJpaTest` find it by searching up the packages. Anything added there reaches every
test. `SpringTestContext_SpringBootTest` and `SpringTestContext_DataJpaTest` test the context itself.

Repository tests extend a small hierarchy:

- `__Repository_TestBase<T, U, V>`: repository, entity and id types; injects the repository (`repositoryInstance()`)
  and the `EntityManager` (`entityManager()`); `identifier(entity)` reads an id through the metamodel.
- `_Repository_DataJpaTest`: `@DataJpaTest` over an embedded H2 in MySQL mode (profile `h2`,
  `application-h2.yaml`, `replace: none`, so Boot's own H2, whose mode rejects upstream's MySQL column definitions, is
  not swapped in), rolled back per test. It imports `_Repository_DataJpaTest_Configuration`, which undoes the MySQL
  settings of `application.yaml` for H2 (`create-drop`, `H2Dialect`, `show_sql` off).
- `_Repository_SpringBootIT`: `@SpringBootTest` against the installed Sakila. Repository calls commit unless the test
  is `@Transactional`. It declares `FindById_Test` for every subclass, and `selectRandom()` /
  `selectRandomPresent()`, which aborts the test by an assumption when the table is empty.

**The injected `EntityManager` is kept, but not used.** `__Repository_TestBase` autowires it and exposes
`entityManager()`, and that stays; the base's own helpers (`identifier()`, `selectRandom()`) are its only
consumers. A repository test never calls `entityManager()` itself: no `persist`, `find`, `flush`, `createQuery`.
Everything a test does with the database goes through the repository under test, `repositoryInstance()`
(`save`, `findById`, `findAll`, ...). A test that needs an entity persisted saves it through the repository.

Naming: `<Repository>_DataJpaTest` and `<Repository>_SpringBootIT`, with **one `@Nested` class per method under
test** (`FindById_Test`, `FindAllByFirstName_Test`, ...), and test methods named `_Empty_Unknown`,
`_NotEmpty_Known`, `__Paged`, `__Transactional`, `_<Exception>_<Why>` and the like. Every class and method carries
javadoc saying what it asserts and why the persistence contexts behave as they do.

### `_DataJpaTest`: fixtures are persisted by the test

The H2 starts empty, so each test persists what it needs, through the upstream randomizer, then sets only the
attributes the test is about (random, unique values such as a UUID string, so the test can count what it finds):

```java
final var actor = ObjectRandomizerUtils.newRandomizedInstanceOf(Actor.class).orElseThrow();
actor.setFirstName(firstName);
repositoryInstance().save(actor);
```

Save through the repository, not `entityManager().persist(...)` (see above), and not
`EntityPersisterUtils.newPersistedInstanceOf(entityManager(), ...)`, which takes the entity manager.

### `_SpringBootIT`: fixtures are existing rows of the actual database, as parameterized tests

An integration test **reads the sample data; it never fabricates rows**. Use the rows the database already holds,
and **as a parameterized test wherever possible**:

- Select the fixture once per class in a `@BeforeAll` instance method (`@TestInstance(Lifecycle.PER_CLASS)` on the
  class), e.g. the first few rows by id through `repositoryInstance().findAll(PageRequest.of(0, 5, JpaSort.of(ASC,
  Actor_.actorId)))`, into a `static` list, and abort with `assumeThat(entities).isNotEmpty()` when the table is empty.
- Each `@Nested` class then feeds that list to its tests with a `private static` `@MethodSource`
  (`entities.stream()`) and a `@ParameterizedTest`, so every selected row is tried, not one.
- The fixture rows are detached. A `@Transactional` test that wants the managed instance reloads the row by id inside
  the transaction (`repositoryInstance().findById(actor.getActorId())`) and compares with `isSameAs` against that.
- `selectRandomPresent()` is for a test that a parameterized form does not fit. Prefer the parameterized form.
- Assert against what the row says (`isEqualToIgnoringCase` on names, since Sakila's collation is case-insensitive),
  and `contains` the fixture row rather than counting results.
- A test that must write does so inside `@Transactional`, so it is rolled back; nothing a test does may outlive it in
  the Sakila database.

A `Stream`-returning query method has to be consumed inside a transaction the caller opened, and closed
(try-with-resources); outside one, Spring Data throws `InvalidDataAccessApiUsageException` before any query runs.
Test both sides.

A `JsonMapper` (Jackson 3) is injectable in a `@SpringBootTest` only; the `@DataJpaTest` slice does not configure
Jackson.

Logging is set in `src/test/resources/logback-spring.xml`, which includes Boot's `base.xml` (console appender, root
at INFO) and sets the levels: `org.hibernate.SQL` at DEBUG and `org.hibernate.orm.jdbc.bind` at TRACE for statements
and bind values, this package at DEBUG so a test's `log.debug(...)` prints, podam at INFO. Keep levels there, not under
`logging.level` in `application.yaml`, and do not add a `logback.xml` / `logback-test.xml`, which Logback would read
before Boot, without Boot's extensions.
