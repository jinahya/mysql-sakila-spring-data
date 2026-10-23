package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Rental;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link RentalRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No rental can be persisted into the H2: a rental is of a copy of a
 * store, by a customer of a store, handled by a staff member of a store, and a store names its manager, a staff
 * member, who works at a store, both {@code NOT NULL}, so none of them can be saved first (see
 * {@link _Repository_DataJpaTest_Fixtures}). Each test here runs its query against an empty {@code rental} table, and
 * asserts an empty page or a zero; that still proves that the JPQL, or the criteria query, is valid and runs on the
 * H2, with the sort the exercise asks for. {@link RentalRepository#countReturnedLate()} is a native query of MySQL's
 * own, which the H2 cannot run, so it has no test here; see {@link RentalRepository_SpringBootIT}.
 *
 * @see RentalRepository_SpringBootIT
 */
class RentalRepository_DataJpaTest
        extends _Repository_DataJpaTest<RentalRepository, Rental, Integer> {

    /**
     * Creates a new instance.
     */
    RentalRepository_DataJpaTest() {
        super(RentalRepository.class, Rental.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link RentalRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, and stays so, so any id misses.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link RentalRepository#countNotReturned()} ({@code SAKILA-RENTAL-B-01}).
     */
    @Nested
    class CountNotReturned_Test {

        /**
         * Asserts that the {@code isNull} criteria count runs on the empty table and counts zero.
         */
        @Test
        void _Zero_NoRentals() {
            final var count = repositoryInstance().countNotReturned();
            assertThat(count).isZero();
        }
    }

    /**
     * Tests {@link RentalRepository#selectMonthlyCounts(Pageable)} ({@code SAKILA-RENTAL-B-02}).
     */
    @Nested
    class SelectMonthlyCounts_Test {

        private static final Sort BY_YEAR_THEN_MONTH = Sort.by("year", "month");

        /**
         * Asserts that the select grouped by the two {@code EXTRACT}s, sorted by the year and then by the month as the
         * exercise sorts, runs on the empty table and selects an empty page, with no months in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().selectMonthlyCounts(PageRequest.of(0, 5, BY_YEAR_THEN_MONTH));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }
}
