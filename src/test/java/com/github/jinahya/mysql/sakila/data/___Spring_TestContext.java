package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence._NoOp;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

/**
 * The Spring context the tests of this project boot against.
 * <p>
 * It lives in test sources on purpose: this project publishes repositories, not an application, so nothing in
 * {@code src/main} should carry a {@code @SpringBootApplication}. A slice test such as {@code @DataJpaTest} finds this
 * by walking up from its own package, so tests in this package and below need no {@code classes} attribute.
 * <p>
 * The entities are outside this package, in the upstream jar, so auto-configuration would not find them; the
 * {@code @EntityScan} points at upstream's {@code _NoOp} marker. The view repositories are plain {@code @Repository}
 * beans that Spring Data does not register and nothing here scans for, so each {@code Impl} is {@code @Import}ed here:
 * {@link FilmListRepositoryImpl}, {@link NicerButSlowerFilmListRepositoryImpl} and {@link SalesByStoreRepositoryImpl}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = _NoOp.class)
@Import({FilmListRepositoryImpl.class, NicerButSlowerFilmListRepositoryImpl.class, SalesByStoreRepositoryImpl.class})
public class ___Spring_TestContext {

}
