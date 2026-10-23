package com.github.jinahya.mysql.sakila.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * The base of every repository of an upstream {@code @Entity}.
 * <p>
 * It is what each {@code <Entity>Repository} of this package extends, so that whatever every repository should offer
 * is declared once, here. Criteria queries go through {@link org.springframework.data.jpa.domain.Specification} and
 * the upstream static metamodel ({@code Film_}, {@code Actor_}, ...), never through attribute names as strings.
 * <p>
 * {@link NoRepositoryBean}: Spring Data must not instantiate this interface itself, only its subinterfaces.
 *
 * @param <T> the type of the entity.
 * @param <ID> the type of the entity's id.
 * @see _NonEntityRepository
 */
@NoRepositoryBean
public interface _Repository<T, ID>
        extends JpaRepository<T, ID>,
                JpaSpecificationExecutor<T> {

}
