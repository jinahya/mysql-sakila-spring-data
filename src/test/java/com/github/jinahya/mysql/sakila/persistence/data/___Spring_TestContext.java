package com.github.jinahya.mysql.sakila.persistence.data;

import com.github.jinahya.mysql.sakila.persistence.__NoOp;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * The Spring context the tests of this project boot against.
 * <p>
 * It lives in test sources on purpose: this project publishes repositories, not an application, so nothing in
 * {@code src/main} should carry a {@code @SpringBootApplication}. A slice test such as {@code @DataJpaTest} finds this
 * by walking up from its own package, so tests in this package and below need no {@code classes} attribute.
 * <p>
 * The entities are outside this package, in the upstream jar, so auto-configuration would not find them; the
 * {@code @EntityScan} points at upstream's {@code __NoOp} marker. The view repositories, when there are any, are plain
 * {@code @Repository} beans that Spring Data does not register and nothing here scans for: {@code @Import} each
 * {@code Impl} here.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = __NoOp.class)
public class ___Spring_TestContext {

}
