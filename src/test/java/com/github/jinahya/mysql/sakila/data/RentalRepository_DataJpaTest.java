package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Rental;
import com.github.jinahya.mysql.sakila.persistence.Rental_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link RentalRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No rental can be persisted into the H2: a rental is of a copy of a
 * store, by a customer of a store, handled by a staff member of a store, and a store names its manager, a staff
 * member, who works at a store, both {@code NOT NULL}, so none of them can be saved first (see
 * {@link _Repository_DataJpaTest_Fixtures}). Each test here runs its query against an empty {@code rental} table, and
 * asserts an empty list or page, or a zero; that still proves that the JPQL, or the criteria query, is valid and runs
 * on the H2, with the sort the exercise asks for, and that the arguments are checked.
 * {@link RentalRepository#listReturnedLate(Pageable)} and {@link RentalRepository#selectLongestWaitsBetweenRentals()}
 * are native queries of MySQL's own, which the H2 cannot run, so they have no test here; see
 * {@link RentalRepository_SpringBootIT}.
 *
 * @see RentalRepository_SpringBootIT
 */
class RentalRepository_DataJpaTest
        extends _Repository_DataJpaTest<RentalRepository, Rental, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Rental_.rentalId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_RENTAL_ID = Sort.by(Sort.Direction.ASC, Rental_.RENTAL_ID);

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
     * Tests {@link RentalRepository#countNotReturned()} ({@code SAKILA_RENTAL_B_01}).
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
     * Tests {@link RentalRepository#selectMonthlyCounts()} ({@code SAKILA_RENTAL_B_02}).
     */
    @Nested
    class SelectMonthlyCounts_Test {

        /**
         * Asserts that the select grouped and ordered by the two {@code EXTRACT}s runs on the empty table and selects
         * an empty list.
         */
        @Test
        void _Empty_NoRentals() {
            final var list = repositoryInstance().selectMonthlyCounts();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link RentalRepository#selectNotReturnedCountsByStore(Pageable)} ({@code SAKILA_RENTAL_I_01}).
     */
    @Nested
    class SelectNotReturnedCountsByStore_Test {

        private static final Sort BY_STORE_ID = Sort.by("st.storeId");

        /**
         * Asserts that the grouped select, sorted by the store id as the exercise sorts, runs on the empty table and
         * selects an empty page, with no stores in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().selectNotReturnedCountsByStore(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectNotReturnedCountsByStore(PageRequest.of(1, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectNotReturnedCountsByStore(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link RentalRepository#selectNotReturnedCountsByDay(Pageable)} ({@code SAKILA_RENTAL_I_02}).
     */
    @Nested
    class SelectNotReturnedCountsByDay_Test {

        private static final Sort BY_DAY = Sort.by("day");

        /**
         * Asserts that the select grouped by the {@code EXTRACT(DATE ..)}, sorted by the day as the exercise sorts,
         * runs on the empty table and selects an empty page, with no days in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().selectNotReturnedCountsByDay(PageRequest.of(0, 5, BY_DAY));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectNotReturnedCountsByDay(PageRequest.of(1, 5, BY_DAY));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectNotReturnedCountsByDay(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link RentalRepository#listOverlapping(Pageable)} ({@code SAKILA_RENTAL_A_03}).
     */
    @Nested
    class ListOverlapping_Test {

        /**
         * Asserts that the criteria query with its correlated {@code EXISTS}, sorted by id, runs on the empty table
         * and lists an empty page, with no rentals in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().listOverlapping(PageRequest.of(0, 5, BY_RENTAL_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().listOverlapping(PageRequest.of(1, 5, BY_RENTAL_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before the method's own check, or any query, runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listOverlapping(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
